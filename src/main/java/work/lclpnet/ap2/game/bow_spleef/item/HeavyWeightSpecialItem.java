package work.lclpnet.ap2.game.bow_spleef.item;

import lombok.Setter;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;
import static work.lclpnet.lobby.util.PlayerReset.resetAttribute;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class HeavyWeightSpecialItem implements SpecialItem {

    public static final String TAG_HEAVY_WEIGHT = "ap2:heavy_weight_egg";
    private static final int DURATION_TICKS = Ticks.seconds(3);
    private final Set<UUID> heavyWeighted = new HashSet<>();
    private @Setter @Nullable DoubleJumpHandler doubleJumpHandler = null;

    @Override
    public String id() {
        return "heavy_weight_egg";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.EGG);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayerEntity player)
                    || !(projectile instanceof EggEntity)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addCommandTag(TAG_HEAVY_WEIGHT);
            ctx.removeSpecialItem(player, this);
        });

        hooks.registerHook(ProjectileHitEntityCallback.HOOK, (projectile, hit) -> {
            if (!projectile.getCommandTags().contains(TAG_HEAVY_WEIGHT)
                    || !(hit.getEntity() instanceof ServerPlayerEntity player)
                    || heavyWeighted.contains(player.getUuid())) return;

            setHeavyWeighted(player);

            Vec3d pos = hit.getPos();
            player.getWorld().playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 0.5f, 0.65f);

            ctx.translations().translateText("game.ap2.bow_spleef.heavy_weighted")
                    .styled(style -> style.withColor(0xff0000))
                    .sendTo(Collections.singleton(player), true);  // TODO unwrap

            ctx.scheduler().timeout(() -> removeHeavyWeighted(player), DURATION_TICKS);
        });
    }

    private void setHeavyWeighted(ServerPlayerEntity player) {
        if (!heavyWeighted.add(player.getUuid())) return;

        if  (doubleJumpHandler != null) {
            doubleJumpHandler.disable(player);
        }

        setAttribute(player, GRAVITY, 0.16);
        setAttribute(player, MOVEMENT_SPEED, 0.075);
    }

    private void removeHeavyWeighted(ServerPlayerEntity player) {
        if (!heavyWeighted.remove(player.getUuid())) return;

        if (doubleJumpHandler != null) {
            doubleJumpHandler.enable(player);
        }

        resetAttribute(player, GRAVITY);
        resetAttribute(player, MOVEMENT_SPEED);
    }

    public boolean isHeavyWeighted(ServerPlayerEntity player) {
        return heavyWeighted.contains(player.getUuid());
    }
}
