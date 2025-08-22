package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
    private final ServerWorld world;
    private final Random random;
    private final Map<UUID, RegistryEntry<ArmorTrimPattern>> patterns = new HashMap<>();

    public CCKitManager(TeamManager teamManager, ServerWorld world, Random random) {
        this.teamManager = teamManager;
        this.world = world;
        this.random = random;
    }

    public void giveItems(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack sword = unbreakable(new ItemStack(Items.IRON_SWORD));
        inventory.setStack(0, sword);

        ItemStack pickaxe = unbreakable(new ItemStack(Items.IRON_PICKAXE));
        inventory.setStack(1, pickaxe);

        ItemStack axe = unbreakable(new ItemStack(Items.IRON_AXE));
        inventory.setStack(2, axe);

        ItemStack shovel = unbreakable(new ItemStack(Items.IRON_SHOVEL));
        inventory.setStack(3, shovel);

        ItemStack hoe = unbreakable(new ItemStack(Items.IRON_HOE));
        inventory.setStack(4, hoe);

        DynamicRegistryManager registryManager = world.getRegistryManager();
        var trimPattern = patterns.computeIfAbsent(player.getUuid(), uuid -> ItemHelper.getRandomTrimPattern(registryManager, random));
        var trimMaterialKey = teamManager.getTeam(player)
                .map(team -> team.key().equals(CozyCampfireInstance.TEAM_RED) ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.LAPIS)
                .orElse(ArmorTrimMaterials.IRON);
        var trimMaterial = ItemHelper.getTrimMaterial(registryManager, trimMaterialKey);

        ItemStack helmet = unbreakable(new ItemStack(Items.IRON_HELMET));
        helmet.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        helmet.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        player.equipStack(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = unbreakable(new ItemStack(Items.IRON_CHESTPLATE));
        chestPlate.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        chestPlate.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        player.equipStack(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = unbreakable(new ItemStack(Items.IRON_LEGGINGS));
        leggings.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        leggings.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        player.equipStack(EquipmentSlot.LEGS, leggings);

        ItemStack boots = unbreakable(new ItemStack(Items.IRON_BOOTS));
        boots.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern));
        boots.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.TRIM, true));
        player.equipStack(EquipmentSlot.FEET, boots);
    }
}
