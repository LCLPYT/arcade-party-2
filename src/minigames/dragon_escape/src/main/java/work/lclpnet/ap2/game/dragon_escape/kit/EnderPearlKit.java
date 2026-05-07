package work.lclpnet.ap2.game.dragon_escape.kit;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.core.hook.EnderPearlTeleportCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.KitOptions;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.ap2.impl.util.CustomNbt;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.ChatFormatting.RED;

public class EnderPearlKit extends SingleItemKit {

    public static final String ID = "ender_pearl";

    private static final MapCodec<Vec3> ORIGIN_CODEC = Vec3.CODEC.fieldOf("ap2:origin");

    private static final double MAX_PROGRESS_SKIP = 0.2;
    private static final int
            RETRY_TICKS = Ticks.seconds(2),
            REFUND_DELAY_TICKS = Ticks.seconds(10);

    private final SplinePath path;
    private final Map<UUID, TaskHandle> refundTasks = new HashMap<>();

    public EnderPearlKit(KitHandle handle, SplinePath path) {
        super(handle, ID, Items.ENDER_PEARL, 1);
        this.path = path;
    }

    @Override
    public void init(KitOptions options) {
        handle.hooks().registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (shooter instanceof ServerPlayer player && projectile instanceof ThrownEnderpearl && handle.hasKitEquipped(player, this)) {
                CustomNbt.set(projectile, ORIGIN_CODEC, player.position());

                UUID uuid = projectile.getUUID();

                refundTasks.put(uuid, handle.scheduler().timeout(() -> {
                    refundTasks.remove(uuid);

                    projectile.discard();

                    refund(player.connection, options);
                }, REFUND_DELAY_TICKS));
            }
        });

        handle.hooks().registerHook(EnderPearlTeleportCallback.HOOK, (owner, enderPearl, pos) -> {
            if (owner instanceof ServerPlayer player && handle.hasKitEquipped(player, this)) {
                TaskHandle refundTask = refundTasks.remove(enderPearl.getUUID());

                if (refundTask != null) {
                    refundTask.cancel();
                }

                if (canTeleportTo(enderPearl, pos)) return false;

                enderPearl.discard();

                handle.translations().translateText("game.ap2.dragon_escape.teleport_too_far")
                        .formatted(RED)
                        .sendTo(player);

                refund(player.connection, options);

                player.getCooldowns().addCooldown(BuiltInRegistries.ITEM.getKey(Items.ENDER_PEARL), RETRY_TICKS);

                return true;
            }

            enderPearl.discard();

            return true;
        });
    }

    private boolean canTeleportTo(ThrownEnderpearl enderPearl, Vec3 target) {
        Vec3 origin = CustomNbt.get(enderPearl, ORIGIN_CODEC).orElse(null);

        if (origin == null) return false;

        double progressFrom = path.getProgress(origin);
        double progressTo = path.getProgress(target);

        return progressTo - progressFrom <= MAX_PROGRESS_SKIP;
    }

    private void refund(ServerGamePacketListenerImpl handler, KitOptions options) {
        if (!handler.isAcceptingMessages()) return;

        ServerPlayer player = handler.player;

        if (player == null || player.isDeadOrDying()) return;

        equip(player, options);

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 0.5f, 1f);
    }
}
