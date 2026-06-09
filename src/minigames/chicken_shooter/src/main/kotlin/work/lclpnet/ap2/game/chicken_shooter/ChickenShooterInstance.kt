package work.lclpnet.ap2.game.chicken_shooter

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.animal.chicken.ChickenVariant
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.core.type.ApVariantHolder
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.DataContainers
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.CardinalAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.ProjectileCanHitCallback
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.translate.Translations
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val DEBUG_CHICKEN_SPAWNS = false
private const val BABY_CHANCE = 0.15
private const val TNT_CHANCE = 0.07
private const val TNT_RADIUS = 7.5
private const val MAX_CHICKEN_SPAWNS = 1000
private val DURATION = 50.seconds

private val BabyChickens = Stat("baby_chickens", 0)
private val TntDetonated = Stat("tnt_detonated", 0)
private val ChickensExploded = Stat("chickens_exploded", 0)

class ChickenShooterInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    private val data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val stats = createStats(data, BabyChickens, TntDetonated, ChickensExploded)
    private val random = Random()
    private val chickenSet = mutableSetOf<Chicken>()
    private lateinit var chickenSpawns: List<Vec3>
    private var despawnHeight = 0
    private var time = 0
    private var spawnInterval = 0

    override fun getData(): DataContainer<ServerPlayer, PlayerRef> = data

    override fun prepare() {
        val world = level

        commons().gameRuleBuilder()
            .set(GameRules.ENTITY_DROPS, false)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)

        despawnHeight = map.requireProperty("despawn-height")

        findChickenSpawner()

        val hooks = gameHandle.hooks

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, _ ->
            val projectile = source.directEntity as? Projectile ?: return@registerWith false
            val chicken = entity as? Chicken ?: return@registerWith false

            projectile.discard()

            if (winManager.isGameOver) return@registerWith false
            val attacker = source.entity as? ServerPlayer ?: return@registerWith false

            val pitch = if (chicken.isBaby) 1.4f else 0.8f
            ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8f, pitch)

            val score = killChicken(chicken, attacker, world)
            commons().addScore(attacker, score, data)

            false
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ -> projectile.discard() }

        ProjectileCanHitCallback.HOOK.registerWith(hooks) { _, entity -> entity is Chicken }

        val scoreboardManager = gameHandle.scoreboardManager

        val objective = scoreboardManager.translateObjective("score", "game.ap2.chicken_shooter.points")
            .formatted(ChatFormatting.YELLOW, ChatFormatting.BOLD)

        useScoreboardStatsSync(data, objective)
        objective.setSlot(DisplaySlot.LIST)
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }

        val team = scoreboardManager.createTeam("team")
        team.setSeeFriendlyInvisibles(true)
        team.collisionRule = Team.CollisionRule.NEVER

        for (player in gameHandle.participants) {
            scoreboardManager.joinTeam(player, team)
            player.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, Int.MAX_VALUE, 1, false, false, false))
        }
    }

    override fun teleportPlayers() {
        val scanBox = map.properties.optJSONArray("spawn-scan-bounds")?.let { MapUtil.readBox(it) } ?: return
        val scanStart = BlockPos.containing(MapUtils.getSpawnPosition(map))
        val spacing = map.properties.optNumber("spawn-spacing", 8.0).toDouble()

        teleportToRandomSpawns(scanBox, listOf(scanStart), spacing)
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, damageSource ->
                damageSource.directEntity is Projectile && entity is Chicken
            }
        }

        val translations = gameHandle.translations
        val playerCount = gameHandle.participants.count()

        spawnInterval = when {
            playerCount > 7 -> 5
            playerCount > 3 -> 7
            else -> 10
        }

        giveBowsToPlayers(translations)

        runEveryTick {
            tick()
        }

        val subject = translations.translateText("game.ap2.chicken_shooter.task")
        commons().createTimer(subject, DURATION.inWholeSeconds.toInt()).whenDone(winManager::complete)
    }

    private fun findChickenSpawner() {
        val chickenBox = MapUtil.readBox(map.requireProperty("chicken-spawn-bounds"))
        val chickenScanStart = MapUtil.readBlockPos(map.requireProperty("chicken-spawn-scan-start"))

        val adjacentBlocks = CardinalAdjacentBlocks {
            chickenBox.contains(it) && level.getBlockState(it).getCollisionShape(level, it).isEmpty
        }

        val scanner = BfsWorldScanner(adjacentBlocks).scan(chickenScanStart)
        val finder = SizedSpaceFinder.create(level, EntityType.CHICKEN)
        val spawns = finder.findSpaces(scanner)

        chickenSpawns = spawns.shuffled().subList(0, MAX_CHICKEN_SPAWNS.coerceAtMost(spawns.size))

        check(chickenSpawns.isNotEmpty()) {
            "Couldn't find any chicken spawns"
        }

        if (DEBUG_CHICKEN_SPAWNS) {
            commons().debugController().renderer().ifPresent { renderer ->
                for (pos in chickenSpawns) {
                    renderer.marker(pos, Blocks.GREEN_CONCRETE.defaultBlockState(), 0x00ff00)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun spawnChicken() {
        val chicken = Chicken(EntityType.CHICKEN, level)

        val variants = level.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT).asHolderIdMap()

        if (variants.size() >= 1) {
            val variant = variants.byId(random.nextInt(variants.size()))

            @Suppress("KotlinConstantConditions")
            (chicken as ApVariantHolder<Holder<ChickenVariant>>).`ap2$setVariant`(variant)
        }

        if (random.nextFloat() < BABY_CHANCE) {
            chicken.isBaby = true
        } else if (random.nextFloat() < TNT_CHANCE) {
            spawnTNT(chicken, level)
        }

        chicken.setPos(chickenSpawns.random())
        level.addFreshEntity(chicken)

        chickenSet.add(chicken)
    }

    private fun spawnTNT(chicken: Chicken, world: ServerLevel) {
        val tnt = PrimedTnt(EntityType.TNT, world)
        tnt.fuse = Int.MAX_VALUE
        tnt.startRiding(chicken, true, false)
        world.addFreshEntity(tnt)
    }

    private fun killChicken(chicken: Chicken, attacker: ServerPlayer, world: ServerLevel): Int {
        if (chicken.isRemoved) return 0

        var score = 0

        val x = chicken.x
        val y = chicken.y
        val z = chicken.z

        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.4, 0.4, 0.4, 0.2)

        val tnt = chicken.firstPassenger as? PrimedTnt

        chicken.discard()
        chickenSet.remove(chicken)

        if (tnt != null) {
            stats.increment(attacker, TntDetonated)
            score += tntExplode(chicken, tnt, world, attacker, x, y, z)
        }

        if (chicken.isBaby) {
            stats.increment(attacker, BabyChickens)
            score += 3
        } else {
            score += 1
        }

        return score
    }

    private fun tntExplode(chicken: Chicken, tnt: PrimedTnt, world: ServerLevel, attacker: ServerPlayer, x: Double, y: Double, z: Double): Int {
        world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 0.5, 0.5, 0.5, 1.0)
        ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.8f, 1.8f)
        ServerPlayerAccess.playSoundToPlayer(attacker, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.7f)

        val tntPos = tnt.position()

        var score = 0
        val affected = world.getEntities(EntityTypeTest.forClass(Chicken::class.java)) {
            tntPos.closerThan(it.position(), TNT_RADIUS)
        }

        var count = 0

        for (c in affected) {
            if (c == chicken || c.isRemoved) continue
            count++
            score += killChicken(c, attacker, world)
        }

        tnt.discard()
        stats.increment(attacker, ChickensExploded, count)

        return score
    }

    private fun giveBowsToPlayers(translations: Translations) {
        val infinity = ItemHelper.getEnchantment(Enchantments.INFINITY, level.registryAccess())

        for (player in gameHandle.participants) {
            val stack = unbreakable(ItemStack(Items.BOW))

            stack.enchant(infinity, 1)
            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.chicken_shooter.bow")
                .styled { it.withItalic(false).applyFormat(ChatFormatting.GOLD) })

            val inventory = player.inventory
            inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
            inventory.setItem(9, ItemStack(Items.ARROW))
        }
    }

    fun tick() {
        if (time % spawnInterval == 0) {
            spawnChicken()
        }

        time++

        val world = level

        chickenSet.removeIf { chicken ->
            val x = chicken.x + 0.5
            val y = chicken.y
            val z = chicken.z + 0.5

            if (y < despawnHeight) {
                (chicken.firstPassenger as? PrimedTnt)?.discard()
                chicken.discard()
                world.sendParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.2, 0.2, 0.2, 0.02)
                return@removeIf true
            }

            false
        }
    }
}
