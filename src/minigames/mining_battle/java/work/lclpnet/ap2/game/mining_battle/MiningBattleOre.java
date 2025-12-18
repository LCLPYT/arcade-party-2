package work.lclpnet.ap2.game.mining_battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.mixin.ServerExplosionAccessor;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.world.ExplosionUtil;
import work.lclpnet.gaco.ds.WeightedList;
import work.lclpnet.kibu.translate.Translations;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static net.minecraft.world.level.block.Blocks.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MiningBattleOre {

    private final Random random;
    private final MiniGameHandle gameHandle;
    private final BiConsumer<ServerPlayer, Integer> scoreConsumer;
    private final Predicate<BlockPos> valid;
    private final Map<Block, Ore> lookup = new HashMap<>();
    private final WeightedList<Ore> ores = new WeightedList<>();

    public MiningBattleOre(Random random, MiniGameHandle gameHandle,
                           BiConsumer<ServerPlayer, Integer> scoreConsumer, Predicate<BlockPos> valid) {
        this.random = random;
        this.gameHandle = gameHandle;
        this.scoreConsumer = scoreConsumer;
        this.valid = valid;
    }

    public void init() {
        registerOre(null, 0, 0.9f);
        registerOre(COAL_ORE, 1, 0.02f);
        registerOre(IRON_ORE, 1, 0.02f);
        registerOre(LAPIS_ORE, 1, 0.02f);
        registerOre(REDSTONE_ORE, 2, 0.01f);
        registerOre(GOLD_ORE, 2, 0.01f);
        registerOre(DIAMOND_ORE, 3, 0.0075f);
        registerOre(EMERALD_ORE, 3, 0.0075f);
        registerOre(RAW_COPPER_BLOCK, 4, 0.008f);
        registerOre(RAW_IRON_BLOCK, 4, 0.008f);
        registerOre(DIAMOND_BLOCK, 5, 0.005f);
        registerOre(RAW_GOLD_BLOCK, 5, 0.005f);
        registerOre(AMETHYST_BLOCK, 0, 0.0025F);
        registerOre(POLISHED_GRANITE, 0, 0.009F);
        registerOre(TNT, 0, 0.005F);
    }

    private void registerOre(@Nullable Block block, int value, float probability) {
        Ore ore = new Ore(block, value);
        ores.add(ore, probability);

        if (block != null) {
            lookup.put(block, ore);
        }
    }

    private int getValue(BlockState state) {
        Ore ore = lookup.get(state.getBlock());
        if (ore == null) return 0;

        return ore.value();
    }

    public boolean isOre(BlockState state) {
        return lookup.containsKey(state.getBlock());
    }

    @Nullable
    public BlockState getRandomState() {
        Ore ore = ores.getRandomElement(random);
        if (ore == null) return null;

        Block block = ore.block();
        if (block == null) return null;

        return block.defaultBlockState();
    }

    public void onOreBroken(ServerPlayer player, BlockPos pos, BlockState broken) {
        if (broken.is(AMETHYST_BLOCK)) {
            weakenOthers(player);
            return;
        }

        if (broken.is(POLISHED_GRANITE)) {
            giveHaste(player);
            return;
        }

        if (broken.is(TNT)) {
            explode(player, pos);
            return;
        }

        int value = getValue(broken);

        if (value > 0) {
            scoreConsumer.accept(player, value);
        }
    }

    private void explode(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();

        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        Vec3 vec = new Vec3(x, y, z);
        float power = 2.1f;

        var explosion = new ServerExplosion(world, null, null, null,
                vec, power, false,
                Explosion.BlockInteraction.KEEP);

        var access = (ServerExplosionAccessor) explosion;

        // mimic behaviour of ServerWorld::createExplosion
        world.gameEvent(null, GameEvent.EXPLODE, vec);
        world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 1.0, 0.0, 0.0, 1);

        BlockState air = AIR.defaultBlockState();

        int totalValue = 0;

        // manually destroy blocks (and count value)
        for (BlockPos exPos : access.invokeCalculateExplodedPositions()) {
            if (!valid.test(exPos)) continue;

            BlockState exState = world.getBlockState(exPos);
            if (exState.isAir()) continue;

            totalValue += getValue(exState);

            world.setBlockAndUpdate(exPos, air);
        }

        if (totalValue > 0) {
            scoreConsumer.accept(player, totalValue);
        }

        // calculate damage and knockback
        access.invokeHurtEntities();

        ExplosionUtil.sendExplosion(world, explosion, ParticleTypes.EXPLOSION);
    }

    private void giveHaste(ServerPlayer player) {
        player.removeEffect(MobEffects.MINING_FATIGUE);

        MobEffectInstance statusEffect = player.getEffect(MobEffects.HASTE);
        int remainingTicks = statusEffect != null ? statusEffect.getDuration() : 0;

        player.removeEffect(MobEffects.HASTE);
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, remainingTicks + 100, 0));
        player.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.5f, 2f);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.mining_battle.haste")
                .formatted(ChatFormatting.GREEN);

        player.sendSystemMessage(msg);
    }

    private void weakenOthers(ServerPlayer player) {
        SoundHelper.playSound(player.level().getServer(), SoundEvents.RAVAGER_CELEBRATE, SoundSource.HOSTILE, 0.5f, 1f);

        Translations translations = gameHandle.getTranslations();

        var playerMsg = translations.translateText(player, "game.ap2.mining_battle.weakened")
                .formatted(ChatFormatting.GREEN);

        player.sendSystemMessage(playerMsg);

        var otherMsg = translations.translateText("game.ap2.mining_battle.weakened_by", styled(player.getScoreboardName(), ChatFormatting.YELLOW))
                .formatted(ChatFormatting.RED);

        for (ServerPlayer other : gameHandle.getParticipants()) {
            if (other == player || other.hasEffect(MobEffects.HASTE)) continue;

            other.removeEffect(MobEffects.MINING_FATIGUE);
            other.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 0), player);

            other.sendSystemMessage(otherMsg.translateFor(other));
        }
    }

    private record Ore(Block block, int value) {}
}
