package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.game.cozy_campfire.CozyCampfireInstance;
import work.lclpnet.ap2.impl.util.ItemHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class CCKitManager {

    private final TeamManager teamManager;
    private final ServerLevel world;
    private final Random random;
    private final Map<UUID, Holder<TrimPattern>> patterns = new HashMap<>();

    public CCKitManager(TeamManager teamManager, ServerLevel world, Random random) {
        this.teamManager = teamManager;
        this.world = world;
        this.random = random;
    }

    public void giveItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        ItemStack sword = unbreakable(new ItemStack(Items.IRON_SWORD));
        inventory.setItem(0, sword);

        ItemStack pickaxe = unbreakable(new ItemStack(Items.IRON_PICKAXE));
        inventory.setItem(1, pickaxe);

        ItemStack axe = unbreakable(new ItemStack(Items.IRON_AXE));
        inventory.setItem(2, axe);

        ItemStack shovel = unbreakable(new ItemStack(Items.IRON_SHOVEL));
        inventory.setItem(3, shovel);

        ItemStack hoe = unbreakable(new ItemStack(Items.IRON_HOE));
        inventory.setItem(4, hoe);

        RegistryAccess registryManager = world.registryAccess();
        var trimPattern = patterns.computeIfAbsent(player.getUUID(), uuid -> ItemHelper.getRandomTrimPattern(registryManager, random));
        var trimMaterialKey = teamManager.getTeam(player)
                .map(team -> team.key().equals(CozyCampfireInstance.TEAM_RED) ? TrimMaterials.REDSTONE : TrimMaterials.LAPIS)
                .orElse(TrimMaterials.IRON);
        var trimMaterial = ItemHelper.getTrimMaterial(registryManager, trimMaterialKey);

        ItemStack helmet = unbreakable(new ItemStack(Items.IRON_HELMET));
        helmet.set(DataComponents.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        helmet.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = unbreakable(new ItemStack(Items.IRON_CHESTPLATE));
        chestPlate.set(DataComponents.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        chestPlate.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        player.setItemSlot(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = unbreakable(new ItemStack(Items.IRON_LEGGINGS));
        leggings.set(DataComponents.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        leggings.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        player.setItemSlot(EquipmentSlot.LEGS, leggings);

        ItemStack boots = unbreakable(new ItemStack(Items.IRON_BOOTS));
        boots.set(DataComponents.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        boots.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true));
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }
}
