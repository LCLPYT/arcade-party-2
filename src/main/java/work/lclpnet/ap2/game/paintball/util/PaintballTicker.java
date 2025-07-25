package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.game.paintball.kit.PaintGunKit;
import work.lclpnet.ap2.impl.game.kit.KitManager;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.Math.max;
import static work.lclpnet.lobby.util.PlayerReset.resetAttribute;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class PaintballTicker {

    private static final float HEAL_PER_SECOND = 4.0f;
    private static final int HEAL_DELAY_TICKS = Ticks.seconds(3);

    private final ServerWorld world;
    private final Participants participants;
    private final PaintballTeams teams;
    private final PaintManager paintManager;
    private final PaintGunManager paintGunManager;
    private final KitManager kitManager;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public PaintballTicker(ServerWorld world, Participants participants, PaintballTeams teams, PaintManager paintManager,
                           PaintGunManager paintGunManager, KitManager kitManager) {
        this.world = world;
        this.participants = participants;
        this.teams = teams;
        this.paintManager = paintManager;
        this.paintGunManager = paintGunManager;
        this.kitManager = kitManager;
    }

    public void start(TaskScheduler scheduler, HookRegistrar hooks) {
        scheduler.interval(this::tick, 1);

        // needs to be registered after the PaintballInstance ALLOW_DAMAGE hook
        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && participants.isParticipating(player)) {
                entry(player).outOfCombatTicks = 0;
            }

            return true;
        });
    }

    private @NotNull Entry entry(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), u -> new Entry());
    }

    private void tick() {
        for (ServerPlayerEntity player : participants) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayerEntity player) {
        Entry entry = entry(player);
        entry.outOfCombatTicks++;

        OnInk onInk = standingOnInk(player);

        if (onInk != OnInk.ENEMY) {
            player.removeStatusEffect(StatusEffects.SLOWNESS);
        }

        if (onInk == OnInk.OWN && player.isSneaking()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20, 0, false, false, true));
            setAttribute(player, EntityAttributes.MOVEMENT_SPEED, 0.14);
            setAttribute(player, EntityAttributes.SNEAKING_SPEED, 1.0);

            paintGunManager.setReloading(player);
            tickReload(player, entry);
            return;
        }

        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        resetAttribute(player, EntityAttributes.MOVEMENT_SPEED);
        resetAttribute(player, EntityAttributes.SNEAKING_SPEED);

        paintGunManager.removeReloading(player);

        if (onInk == OnInk.ENEMY) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1, false, false, false));
        }
    }

    private void tickReload(ServerPlayerEntity player, Entry entry) {

        if (entry.outOfCombatTicks >= HEAL_DELAY_TICKS) {
            player.setHealth(player.getHealth() + HEAL_PER_SECOND / 20);
        }

        teams.teamOf(player).ifPresent(team -> world.spawnParticles(
                new DustParticleEffect(team.key().color(), 0.8f), player.getX(), player.getY(), player.getZ(),
                2, 0.2, 0, 0.2, 0.2));

        for (ItemStack stack : player.getInventory()) {
            if (!(SingleItemKit.get(stack, kitManager).orElse(null) instanceof PaintGunKit kit)) continue;

            PaintGun paintGun = kit.getPaintGun();

            if (stack.getDamage() <= 0) continue;

            if (entry.reloadTicks >= paintGun.reloadTicks()) {
                entry.reloadTicks = 0;
                stack.set(DataComponentTypes.DAMAGE, max(0, stack.getDamage() - paintGun.reloadAmount()));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.2f, 1f);
            } else {
                entry.reloadTicks++;
            }

            break;
        }
    }

    private @NotNull OnInk standingOnInk(ServerPlayerEntity player) {
        PaintballTeam team = teams.teamOf(player).orElse(null);

        if (team == null) {
            return OnInk.NONE;
        }

        double width = player.getDimensions(player.getPose()).width();
        boolean onOwnInk = false;

        for (BlockPos pos : BlockBox.of(Box.of(player.getPos(), width, 0.1, width))) {
            BlockState state = world.getBlockState(pos);
            DyeTeamKey paintTeam = paintManager.getTeam(state.getBlock());

            if (paintTeam == null) continue;

            if (paintTeam != team.key()) {
                return OnInk.ENEMY;
            }

            onOwnInk = true;
        }

        return onOwnInk ? OnInk.OWN : OnInk.NONE;
    }

    private enum OnInk { NONE, ENEMY, OWN }

    private static class Entry {
        int reloadTicks = 0;
        int outOfCombatTicks = 0;
    }
}
