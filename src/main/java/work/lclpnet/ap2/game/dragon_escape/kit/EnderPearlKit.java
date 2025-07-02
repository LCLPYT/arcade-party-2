package work.lclpnet.ap2.game.dragon_escape.kit;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.core.hook.EnderPearlTeleportCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.util.Formatting.RED;

public class EnderPearlKit extends SingleItemKit {

    public static final String ID = "ender_pearl";

    private static final MapCodec<Vec3d> ORIGIN_CODEC = Vec3d.CODEC.fieldOf("ap2:origin");

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
    public void init() {
        handle.hooks().registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (shooter instanceof ServerPlayerEntity player && projectile instanceof EnderPearlEntity && handle.hasKitEquipped(player, this)) {
                EntityUtil.putCustomData(projectile, ORIGIN_CODEC, player.getPos());

                UUID uuid = projectile.getUuid();

                refundTasks.put(uuid, handle.scheduler().timeout(() -> {
                    refundTasks.remove(uuid);

                    projectile.discard();

                    refund(player.networkHandler);
                }, REFUND_DELAY_TICKS));
            }
        });

        handle.hooks().registerHook(EnderPearlTeleportCallback.HOOK, (owner, enderPearl, pos) -> {
            if (owner instanceof ServerPlayerEntity player && handle.hasKitEquipped(player, this)) {
                TaskHandle refundTask = refundTasks.remove(enderPearl.getUuid());

                if (refundTask != null) {
                    refundTask.cancel();
                }

                if (canTeleportTo(enderPearl, pos)) return false;

                enderPearl.discard();

                handle.translations().translateText("game.ap2.dragon_escape.teleport_too_far")
                        .formatted(RED)
                        .sendTo(player);

                player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.NEUTRAL, 0.5f, 1f);

                equip(player);

                player.getItemCooldownManager().set(Registries.ITEM.getId(Items.ENDER_PEARL), RETRY_TICKS);

                return true;
            }

            enderPearl.discard();

            return true;
        });
    }

    private boolean canTeleportTo(EnderPearlEntity enderPearl, Vec3d target) {
        Vec3d origin = EntityUtil.getCustomData(enderPearl, ORIGIN_CODEC).orElse(null);

        if (origin == null) return false;

        double progressFrom = path.getProgress(origin);
        double progressTo = path.getProgress(target);

        return progressTo - progressFrom <= MAX_PROGRESS_SKIP;
    }

    private void refund(ServerPlayNetworkHandler handler) {
        if (!handler.isConnectionOpen()) return;

        ServerPlayerEntity player = handler.player;

        if (player == null || player.isDead()) return;

        equip(player);

        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.NEUTRAL, 0.5f, 1f);
    }
}
