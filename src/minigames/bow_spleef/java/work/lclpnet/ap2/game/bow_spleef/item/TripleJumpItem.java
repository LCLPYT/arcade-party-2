package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.hook.util.PlayerUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TripleJumpItem implements SpecialItem {

    private static final int USES = 1;
    private final Set<UUID> tripleJump = new HashSet<>();

    @Override
    public String id() {
        return "triple_jump";
    }

    @Override
    public ItemStack createItemStack(RegistryAccess registryManager) {
        return new ItemStack(Items.GOLDEN_BOOTS, 3);
    }

    @Override
    public void onDropped(ServerPlayer player) {
        tripleJump.remove(player.getUUID());
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        PlayerUtils.syncPlayerItems(player);
        return InteractionResult.FAIL;
    }

    public boolean handleExtraJump(ServerPlayer player, SpecialItemContext ctx) {
        UUID uuid = player.getUUID();

        if (tripleJump.contains(uuid)) {
            tripleJump.remove(uuid);

            ItemStack stack = player.getInventory().getItem(8);
            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamageValue();
            int dmg = (int) Math.ceil((float) maxDamage / USES);

            if (damage + dmg >= maxDamage) {
                ctx.removeSpecialItem(player, this);
            } else {
                if (player.getInventory().getSelectedSlot() == 8) {
                    stack.hurtAndBreak(dmg, player, EquipmentSlot.MAINHAND);
                } else {
                    stack.hurtWithoutBreaking(dmg, player);
                }
            }

            return false;
        }

        tripleJump.add(uuid);

        return true;
    }
}
