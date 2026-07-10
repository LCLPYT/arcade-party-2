package work.lclpnet.ap2.deadline

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import work.lclpnet.kibu.scheduler.Ticks
import java.util.Random
import java.util.UUID
import kotlin.math.roundToInt

private val WORLD_BORDER_DELAY = Ticks.minutes(3).toLong()
private val WORLD_BORDER_TIME = Ticks.minutes(1).toLong()

class DeadlineInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap, private val schema: DeadlineMapSchema) :
    EliminationGameInstance(gameHandle, level, map) {

    private val random = Random()
    private val colors = HashMap<UUID, DyeColor>()
    private val cycles = HashMap<UUID, LightCycle>()
    private val trail = LightTrail(level)
    private val powerUps = PowerUps(gameHandle, map, level, random, commons().debugController()) { cycles[it] }

    override fun teleportPlayers() {
        val spawnBox = requireNotNull(schema.spawnBox) {
            "Map property \"Spawn box\" is not set in the deadline schema"
        }

        teleportToRandomSpawns(spawnBox, schema.scanStarts)
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        useSmoothDeath()

        gameHandle.protect { config ->
            // riders caught outside the shrinking world border take damage until they die
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { _, damageSource ->
                damageSource.isOf(DamageTypes.OUTSIDE_BORDER)
            }
        }

        val team = createTeam()
        assignColors()
        initHooks()
        spawnMounts(team)
    }

    override fun go() {
        eliminateBelowCriticalHeight()
        powerUps.spawn(schema.powerUpSpawns)
        powerUps.startRefreshing(schema.powerUpSpawns)

        // the border closes in after a while, so the game is guaranteed to end
        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, 0)

        gameHandle.rootScheduler.interval(1) { -> tick() }
    }

    private fun tick() {
        trail.tick()

        for (player in gameHandle.participants) {
            val cycle = cycles[player.uuid] ?: continue

            val before = cycle.sheep.position()
            cycle.tick(player.lastClientInput)
            val movement = cycle.sheep.position().subtract(before)

            // crashing into a wall or driving into a trail. phased riders pass through trails
            if (cycle.crashed || (!cycle.phased && trail.collides(player.boundingBox, movement, player.uuid))) {
                eliminate(player)
                continue
            }

            trail.extend(player.uuid, cycle.sheep.position(), colors.getValue(player.uuid))
            showSpeed(player, cycle)
        }
    }

    // show the rider's speed on the xp bar in km/h
    private fun showSpeed(rider: ServerPlayer, cycle: LightCycle) {
        val kmh = (cycle.speed * 3.6f).roundToInt()
        rider.connection.send(ClientboundSetExperiencePacket(cycle.speedFraction, 0, kmh))
    }

    // reset the xp bar readout when a rider stops riding
    private fun clearSpeed(rider: ServerPlayer) {
        rider.connection.send(ClientboundSetExperiencePacket(0f, 0, 0))
    }

    // intentionally not calling super.onDeath -> no equipment/experience drops
    override fun onDeath(player: ServerPlayer, attacker: Entity?) {}

    override fun participantRemoved(player: ServerPlayer) {
        player.vehicle?.discard()
        cycles.remove(player.uuid)
        trail.discard(player.uuid)
        clearSpeed(player)
        super.participantRemoved(player)
    }

    private fun assignColors() {
        val palette = ColorUtil.VIVID_DYE_COLORS.shuffled(random)
        var i = 0
        for (player in gameHandle.participants) {
            colors[player.uuid] = palette[i++ % palette.size]
        }
    }

    private fun createTeam(): PlayerTeam {
        val manager = gameHandle.scoreboardManager
        val team = manager.createTeam("deadline")
        team.collisionRule = Team.CollisionRule.NEVER
        manager.joinTeam(gameHandle.participants, team)
        return team
    }

    private fun initHooks() {
        // riders cannot leave their sheep
        EntityDismountCallback.HOOK.registerWith(hooks) { entity, _ -> entity is ServerPlayer }

        // clean up the mount if a rider disconnects
        ServerPlayConnectionHooks.DISCONNECT.registerWith(hooks) { handler, _ ->
            handler.player.vehicle?.discard()
        }

        powerUps.initHooks()
    }

    private fun spawnMounts(team: PlayerTeam) {
        // players were already teleported to their spawns by the base start sequence
        for (player in gameHandle.participants) {
            val color = colors.getValue(player.uuid)
            val sheep = spawnSheep(player, color)
            cycles[player.uuid] = LightCycle(sheep)
            gameHandle.scoreboardManager.joinTeam(sheep, team)
            equip(player, color)
        }
    }

    // dress the rider in leather armor matching their sheep and trail
    private fun equip(player: ServerPlayer, color: DyeColor) {
        val colorInt = color.textureDiffuseColor

        player.setItemSlot(EquipmentSlot.HEAD, getLeatherArmor(Items.LEATHER_HELMET, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, colorInt).unbreakable())
    }

    private fun spawnSheep(player: ServerPlayer, color: DyeColor): Sheep {
        val world = player.level()
        val sheep = Sheep(EntityTypes.SHEEP, world)

        sheep.setNoAi(true)
        sheep.isInvulnerable = true
        sheep.isSilent = true
        sheep.setColor(color)
        sheep.setYRot(player.yRot)
        sheep.setYBodyRot(player.yRot)
        sheep.setPosRaw(player.x, player.y, player.z)

        world.addFreshEntity(sheep)
        player.startRiding(sheep, true, false)

        return sheep
    }
}
