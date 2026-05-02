package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.core.hook.ProjectileHitEntityCallback;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;

import java.util.Set;

public class SwitcherItem implements SpecialItem {

    public static final String TAG_SWITCHER = "ap2:switcher";

    @Override
    public String id() {
        return "switcher";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.SNOWBALL);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayer player)
                    || !(projectile instanceof Snowball)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addTag(TAG_SWITCHER);
            ctx.removeSpecialItem(player, this);
        });

        hooks.registerHook(ProjectileHitEntityCallback.HOOK, (projectile, hit) -> {
            if (!projectile.entityTags().contains(TAG_SWITCHER)
                    || !(projectile.getOwner() instanceof ServerPlayer shooter)
                    || !(hit.getEntity() instanceof ServerPlayer victim)) return;

            Vec3 victimPos = victim.position();
            float victimYaw = victim.getYRot();
            float victimPitch = victim.getXRot();

            ServerLevel world = shooter.level();
            victim.teleportTo(world, shooter.getX(), shooter.getY(), shooter.getZ(), Set.of(), shooter.getYRot(), shooter.getXRot(), true);
            shooter.teleportTo(world, victimPos.x(), victimPos.y(), victimPos.z(), Set.of(), victimYaw, victimPitch, true);

            ServerPlayerAccess.playSoundToPlayer(victim, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 2f);
            ServerPlayerAccess.playSoundToPlayer(shooter, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 2f);
        });
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        PlayerInventoryAccess.setSelectedSlot(player, 8);
        return InteractionResult.PASS;
    }
}
