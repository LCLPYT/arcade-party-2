package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.core.hook.ProjectileShootCallback;
import work.lclpnet.ap2.game.bow_spleef.BowSpleefInstance;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.ap2.impl.util.world.ExplosionUtil;
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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.TNT);
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(ProjectileShootCallback.HOOK, (shooter, projectile) -> {
            if (!(shooter instanceof ServerPlayer player)
                    || !(projectile instanceof Arrow)
                    || !ctx.hasSpecialItem(player, this)) return;

            projectile.addTag(TAG_EXPLOSIVE);
            ctx.removeSpecialItem(player, this);
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.5f, 1.75f);
        });

        hooks.registerHook(impactHook, (projectile, blockPos) -> {
            if (!(projectile.level() instanceof ServerLevel world)
                    || !projectile.getTags().contains(TAG_EXPLOSIVE)) return;

            var behaviour = new ExplosionDamageCalculator() {

                @Override
                public float getKnockbackMultiplier(Entity entity) {
                    return 2f;
                }
            };

            Vec3 pos = blockPos.above().getCenter();

            world.explode(projectile, null, behaviour, pos.x, pos.y, pos.z, 3f, false,
                    Level.ExplosionInteraction.BLOCK, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                    ExplosionUtil.EXPLOSION_BLOCK_PARTICLES,
                    SoundEvents.GENERIC_EXPLODE);
        });
    }
}
