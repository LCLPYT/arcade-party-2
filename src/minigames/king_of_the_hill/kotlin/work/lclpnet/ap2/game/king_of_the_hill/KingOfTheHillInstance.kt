package work.lclpnet.ap2.game.king_of_the_hill

import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.GoatEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting
import net.minecraft.world.GameRules
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.DataContainers
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.lobby.game.api.prot.scope.EntityDamageSourceScope
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import work.lclpnet.lobby.game.map.GameMap
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val DURATION_SECONDS = 160

class KingOfTheHillInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrapFunction {
    
    private val data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    var goalShape: BlockShape? = null

    init {
        useOldCombat()
    }

    override fun getData() = data!!

    override fun bootstrapWorld(world: ServerWorld, map: GameMap) = createMarkers(world, map)

    override fun prepare() {
        commons().teleportToRandomSpawns(Random.asJavaRandom())
        goalShape = MapUtil.readShape(map, "goal-shape")

        setupSidebarScoreboard(data)

        commons().gameRuleBuilder()
            .set(GameRules.SNOW_ACCUMULATION_HEIGHT, 0)
            .set(GameRules.DO_WEATHER_CYCLE, false)
            .set(GameRules.FALL_DAMAGE, false)
            .set(GameRules.ANNOUNCE_ADVANCEMENTS, false)

        commons().addWaypoint(goalShape!!.center().toCenterPos(), 0xffd700)
    }

    override fun go() {
        val readShape = MapUtil.readOptShape(map, "spawn-remove-shape")

        readShape?.forEach { pos ->
            world.setBlock(pos, Blocks.AIR)
        }

        val name = translate("game.ap2.king_of_the_hill.knockback_stick").formatted(Formatting.GOLD)
        val knockback = ItemHelper.getEnchantment(Enchantments.KNOCKBACK, world.registryManager)

        for (player in players()) {
            val stack = ItemStack(Items.STICK)
            stack.set(DataComponentTypes.ITEM_NAME, name.translateFor(player))
            stack.addEnchantment(knockback, 1)

            player.inventory.setStack(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)

            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE,
                Integer.MAX_VALUE, 255, false, false, false))
        }

        gameHandle.protect { config ->
            config.allow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope {entity, source ->
                entity is ServerPlayerEntity
                        && players().isParticipating(entity)
                        && (source.isOf(DamageTypes.PLAYER_ATTACK) || source.attacker is GoatEntity)
            })
        }

        interval(20) { ->
            val inGoal = players().stream().filter { goalShape!!.contains(it.entityPos) }.toList()

            if (inGoal.size == 1) {
                commons().addScore(inGoal[0], 1, data)
                inGoal[0].playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.6f)
            }
        }

        gameHandle.hooks.registerHook(PlayerInventoryHooks.SLOT_CHANGE, PlayerInventoryHooks.SlotChange { player, i ->
            if (players().isParticipating(player) && i != 4) {
                PlayerInventoryAccess.setSelectedSlot(player, 4)
            }
        })

        useTaskTimer(DURATION_SECONDS).whenDone { winManager.complete() }
    }
}
