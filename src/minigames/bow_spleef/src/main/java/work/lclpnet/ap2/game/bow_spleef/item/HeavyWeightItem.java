package work.lclpnet.ap2.game.bow_spleef.item;

import lombok.Setter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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

import static net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
import static work.lclpnet.ap2.impl.util.EntityUtil.resetAttribute;
import static work.lclpnet.ap2.impl.util.EntityUtil.setAttribute;

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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.EGG);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        ProjectileShootCallback.HOOK.registerWith(hooks, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayer player)
                    || !(projectile instanceof ThrownEgg)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addTag(TAG_HEAVY_WEIGHT);
            ctx.removeSpecialItem(player, this);
        });

        ProjectileHitEntityCallback.HOOK.registerWith(hooks, (projectile, hit) -> {
            if (!projectile.entityTags().contains(TAG_HEAVY_WEIGHT)
                    || !(hit.getEntity() instanceof ServerPlayer player)
                    || heavyWeighted.contains(player.getUUID())) return;

            setHeavyWeighted(player);

            Vec3 pos = hit.getLocation();
            ServerLevel world = player.level();
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, 0.5f, 0.65f);
            world.sendParticles(ParticleTypes.FALLING_NECTAR, pos.x, pos.y + 1, pos.z, 100, 0.25, 0.5, 0.25, 1);

            ctx.translations().translateText("game.ap2.bow_spleef.heavy_weighted")
                    .styled(style -> style.withColor(0xff0000))
                    .sendTo(player, true);

            ctx.scheduler().timeout(() -> removeHeavyWeighted(player), DURATION_TICKS);
        });
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        PlayerInventoryAccess.setSelectedSlot(player, 8);
        return InteractionResult.PASS;
    }

    private void setHeavyWeighted(ServerPlayer player) {
        if (!heavyWeighted.add(player.getUUID())) return;

        if  (doubleJumpHandler != null) {
            doubleJumpHandler.disable(player);
        }

        setAttribute(player, GRAVITY, 0.14);
        setAttribute(player, MOVEMENT_SPEED, 0.075);
    }

    private void removeHeavyWeighted(ServerPlayer player) {
        if (!heavyWeighted.remove(player.getUUID())) return;

        if (doubleJumpHandler != null) {
            doubleJumpHandler.enable(player);
        }

        resetAttribute(player, GRAVITY);
        resetAttribute(player, MOVEMENT_SPEED);
    }

    public boolean isHeavyWeighted(ServerPlayer player) {
        return heavyWeighted.contains(player.getUUID());
    }
}
