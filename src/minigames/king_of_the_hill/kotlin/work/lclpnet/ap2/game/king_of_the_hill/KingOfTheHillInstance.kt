package work.lclpnet.ap2.game.king_of_the_hill

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Blocks
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

    override fun bootstrapWorld(world: ServerLevel, map: GameMap) = createMarkers(world, map)

    override fun prepare() {
        commons().teleportToRandomSpawns(Random.asJavaRandom())
        goalShape = MapUtil.readShape(map, "goal-shape")

        setupSidebarScoreboard(data)

        commons().gameRuleBuilder()
            .set(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT, 0)
            .set(GameRules.RULE_WEATHER_CYCLE, false)
            .set(GameRules.RULE_FALL_DAMAGE, false)
            .set(GameRules.RULE_ANNOUNCE_ADVANCEMENTS, false)

        commons().addWaypoint(goalShape!!.center().center, 0xffd700)
    }

    override fun go() {
        val readShape = MapUtil.readOptShape(map, "spawn-remove-shape")

        readShape?.forEach { pos ->
            world.setBlock(pos, Blocks.AIR)
        }

        val name = translate("game.ap2.king_of_the_hill.knockback_stick").formatted(ChatFormatting.GOLD)
        val knockback = ItemHelper.getEnchantment(Enchantments.KNOCKBACK, world.registryAccess())

        for (player in players()) {
            val stack = ItemStack(Items.STICK)
            stack.set(DataComponents.ITEM_NAME, name.translateFor(player))
            stack.enchant(knockback, 1)

            player.inventory.setItem(4, stack)
            PlayerInventoryAccess.setSelectedSlot(player, 4)

            player.addEffect(
                MobEffectInstance(
                    MobEffects.RESISTANCE,
                Integer.MAX_VALUE, 255, false, false, false))
        }

        gameHandle.protect { config ->
            config.allow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope {entity, source ->
                entity is ServerPlayer
                        && players().isParticipating(entity)
                        && (source.`is`(DamageTypes.PLAYER_ATTACK) || source.entity is Goat)
            })
        }

        interval(20) { ->
            val inGoal = players().stream().filter { goalShape!!.contains(it.position()) }.toList()

            if (inGoal.size == 1) {
                commons().addScore(inGoal[0], 1, data)
                inGoal[0].playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f)
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
