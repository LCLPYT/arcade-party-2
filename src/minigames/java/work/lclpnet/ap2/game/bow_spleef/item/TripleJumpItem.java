package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return new ItemStack(Items.GOLDEN_BOOTS, 3);
    }

    @Override
    public void onDropped(ServerPlayerEntity player) {
        tripleJump.remove(player.getUuid());
    }

    @Override
    public ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        PlayerUtils.syncPlayerItems(player);
        return ActionResult.FAIL;
    }

    public boolean handleExtraJump(ServerPlayerEntity player, SpecialItemContext ctx) {
        UUID uuid = player.getUuid();

        if (tripleJump.contains(uuid)) {
            tripleJump.remove(uuid);

            ItemStack stack = player.getInventory().getStack(8);
            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamage();
            int dmg = (int) Math.ceil((float) maxDamage / USES);

            if (damage + dmg >= maxDamage) {
                ctx.removeSpecialItem(player, this);
            } else {
                if (player.getInventory().getSelectedSlot() == 8) {
                    stack.damage(dmg, player, EquipmentSlot.MAINHAND);
                } else {
                    stack.damage(dmg, player);
                }
            }

            return false;
        }

        tripleJump.add(uuid);

        return true;
    }
}
