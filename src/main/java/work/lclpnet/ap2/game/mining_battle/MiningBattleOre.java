package work.lclpnet.ap2.game.mining_battle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static net.minecraft.block.Blocks.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class MiningBattleOre {

    private final Random random;
    private final MiniGameHandle gameHandle;
    private final BiConsumer<ServerPlayerEntity, Integer> scoreConsumer;
    private final Predicate<BlockPos> valid;
    private final Map<Block, Ore> lookup = new HashMap<>();
    private final WeightedList<Ore> ores = new WeightedList<>();

    public MiningBattleOre(Random random, MiniGameHandle gameHandle,
                           BiConsumer<ServerPlayerEntity, Integer> scoreConsumer, Predicate<BlockPos> valid) {
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
        registerOre(AMETHYST_BLOCK, 0, 0.003F);
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

        return block.getDefaultState();
    }

    public void onOreBroken(ServerPlayerEntity player, BlockPos pos, BlockState broken) {
        if (broken.isOf(AMETHYST_BLOCK)) {
            weakenOthers(player);
            return;
        }

        if (broken.isOf(POLISHED_GRANITE)) {
            giveHaste(player);
            return;
        }

        if (broken.isOf(TNT)) {
            explode(player, pos);
            return;
        }

        int value = getValue(broken);

        if (value > 0) {
            scoreConsumer.accept(player, value);
        }
    }

    private void explode(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();

        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        float power = 2.1f;

        Explosion explosion = new Explosion(world, null, null, null,
                x, y, z, power, false,
                Explosion.DestructionType.KEEP, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.ENTITY_GENERIC_EXPLODE);

        explosion.collectBlocksAndDamageEntities();
        explosion.affectWorld(true);

        BlockState air = AIR.getDefaultState();

        int totalValue = 0;

        for (BlockPos exPos : explosion.getAffectedBlocks()) {
            if (!valid.test(exPos)) continue;

            BlockState exState = world.getBlockState(exPos);
            if (exState.isAir()) continue;

            totalValue += getValue(exState);

            world.setBlockState(exPos, air);
        }

        if (totalValue > 0) {
            scoreConsumer.accept(player, totalValue);
        }

        explosion.clearAffectedBlocks();

        for (ServerPlayerEntity other : world.getPlayers()) {
            if (!(other.squaredDistanceTo(x, y, z) < 4096.0)) continue;

            other.networkHandler.sendPacket(new ExplosionS2CPacket(x, y, z, power, explosion.getAffectedBlocks(),
                    explosion.getAffectedPlayers().get(other), explosion.getDestructionType(), explosion.getParticle(),
                    explosion.getEmitterParticle(), explosion.getSoundEvent()));
        }
    }

    private void giveHaste(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.HASTE);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 100, 0));
        player.playSound(SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 0.5f, 2f);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.mining_battle.haste")
                .formatted(Formatting.GREEN);

        player.sendMessage(msg);
    }

    private void weakenOthers(ServerPlayerEntity player) {
        SoundHelper.playSound(player.getServer(), SoundEvents.ENTITY_RAVAGER_CELEBRATE, SoundCategory.HOSTILE, 0.5f, 1f);

        TranslationService translations = gameHandle.getTranslations();

        var playerMsg = translations.translateText(player, "game.ap2.mining_battle.weakened")
                .formatted(Formatting.GREEN);

        player.sendMessage(playerMsg);

        var otherMsg = translations.translateText("game.ap2.mining_battle.weakened_by", styled(player.getNameForScoreboard(), Formatting.YELLOW))
                .formatted(Formatting.RED);

        for (ServerPlayerEntity other : gameHandle.getParticipants()) {
            if (other == player) continue;

            other.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            other.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 150, 0), player);

            other.sendMessage(otherMsg.translateFor(player));
        }
    }

    private record Ore(Block block, int value) {}
}
