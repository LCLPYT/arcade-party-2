package work.lclpnet.ap2.game.bow_spleef.item;

import lombok.Setter;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.handler.DoubleJumpHandler;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.entity.attribute.EntityAttributes.GRAVITY;
import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;
import static work.lclpnet.lobby.util.PlayerReset.resetAttribute;
import static work.lclpnet.lobby.util.PlayerReset.setAttribute;

public class HeavyWeightItem implements SpecialItem {

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
            ServerWorld world = player.getWorld();
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 0.5f, 0.65f);
            world.spawnParticles(ParticleTypes.FALLING_NECTAR, pos.x, pos.y + 1, pos.z, 100, 0.25, 0.5, 0.25, 1);

            ctx.translations().translateText("game.ap2.bow_spleef.heavy_weighted")
                    .styled(style -> style.withColor(0xff0000))
                    .sendTo(player, true);

            ctx.scheduler().timeout(() -> removeHeavyWeighted(player), DURATION_TICKS);
        });
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        PlayerInventoryAccess.setSelectedSlot(player, 8);
        return ActionResult.PASS;
    }

    private void setHeavyWeighted(ServerPlayerEntity player) {
        if (!heavyWeighted.add(player.getUuid())) return;

        if  (doubleJumpHandler != null) {
            doubleJumpHandler.disable(player);
        }

        setAttribute(player, GRAVITY, 0.14);
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
