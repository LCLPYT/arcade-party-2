package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.game.bow_spleef.BowSpleefInstance;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookRegistrar;

public class ExplodeAmmoItem implements SpecialItem {

    public static final String TAG_EXPLOSIVE = "ap2:explosive";
    private final Hook<BowSpleefInstance.Impact> impactHook;

    public ExplodeAmmoItem(Hook<BowSpleefInstance.Impact> impactHook) {
        this.impactHook = impactHook;
    }

    @Override
    public String id() {
        return "explode_ammo";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.TNT);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayerEntity player)
                    || !(projectile instanceof ArrowEntity)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addCommandTag(TAG_EXPLOSIVE);
            ctx.removeSpecialItem(player, this);
            player.getServerWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 0.5f, 1.75f);
        });

        hooks.registerHook(impactHook, (projectile, blockPos) -> {
            if (!(projectile.getWorld() instanceof ServerWorld world)
                    || !projectile.getCommandTags().contains(TAG_EXPLOSIVE)) return;

            var behaviour = new ExplosionBehavior() {

                @Override
                public float getKnockbackModifier(Entity entity) {
                    return 2f;
                }
            };

            Vec3d pos = blockPos.up().toCenterPos();

            world.createExplosion(projectile, null, behaviour, pos.x, pos.y, pos.z, 3f, false,
                    World.ExplosionSourceType.BLOCK, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                    SoundEvents.ENTITY_GENERIC_EXPLODE);
        });
    }
}
