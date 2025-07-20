package work.lclpnet.ap2.game.paintball.kit;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.game.paintball.util.PaintGunManager;
import work.lclpnet.ap2.impl.game.kit.KitHandle;
import work.lclpnet.ap2.impl.game.kit.SingleItemKit;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;

public class RifleKit extends SingleItemKit {

    public static final String ID = "rifle";

    private final PaintGunManager paintGunManager;

    public RifleKit(KitHandle handle, PaintGunManager paintGunManager) {
        super(handle, ID, Items.IRON_HORSE_ARMOR, 1);
        this.paintGunManager = paintGunManager;
    }

    @Override
    public void init() {
        handle.hooks().registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayerEntity player)) {
                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (!stack.isOf(getItem())) {
                return ActionResult.PASS;
            }

            paintGunManager.shoot(player);
//            shoot(player);

            return ActionResult.SUCCESS;
        });
    }

    private void shoot(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();

        var bullet = new SnowballEntity(EntityType.SNOWBALL, world);
        bullet.setPosition(player.getEyePos());
        bullet.setOwner(player);

        Vec3d dir = player.getRotationVector();
        double power = 1.2;
        bullet.setVelocity(dir.multiply(power));

        world.spawnEntity(bullet);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 2f);

        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 3,
                0.2, 0.2, 0.2, 0.1);
    }
}
