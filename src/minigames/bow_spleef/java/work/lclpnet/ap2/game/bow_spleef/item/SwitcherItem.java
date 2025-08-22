package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;

import java.util.Set;

public class SwitcherItem implements SpecialItem {

    public static final String TAG_SWITCHER = "ap2:switcher";

    @Override
    public String id() {
        return "switcher";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.SNOWBALL);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayerEntity player)
                    || !(projectile instanceof SnowballEntity)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addCommandTag(TAG_SWITCHER);
            ctx.removeSpecialItem(player, this);
        });

        hooks.registerHook(ProjectileHitEntityCallback.HOOK, (projectile, hit) -> {
            if (!projectile.getCommandTags().contains(TAG_SWITCHER)
                    || !(projectile.getOwner() instanceof ServerPlayerEntity shooter)
                    || !(hit.getEntity() instanceof ServerPlayerEntity victim)) return;

            Vec3d victimPos = victim.getPos();
            float victimYaw = victim.getYaw();
            float victimPitch = victim.getPitch();

            ServerWorld world = shooter.getWorld();
            victim.teleport(world, shooter.getX(), shooter.getY(), shooter.getZ(), Set.of(), shooter.getYaw(), shooter.getPitch(), true);
            shooter.teleport(world, victimPos.getX(), victimPos.getY(), victimPos.getZ(), Set.of(), victimYaw, victimPitch, true);

            victim.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 2f);
            shooter.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 2f);
        });
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        PlayerInventoryAccess.setSelectedSlot(player, 8);
        return ActionResult.PASS;
    }
}
