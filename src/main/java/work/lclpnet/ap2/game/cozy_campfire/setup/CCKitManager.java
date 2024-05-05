package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.game.cozy_campfire.CozyCampfireInstance;
import work.lclpnet.ap2.impl.util.ItemStackHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        inventory.setStack(0, sword);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        inventory.setStack(1, pickaxe);

        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        inventory.setStack(2, axe);

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        shovel.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        inventory.setStack(3, shovel);

        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        hoe.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        inventory.setStack(4, hoe);

        DynamicRegistryManager registryManager = world.getRegistryManager();
        var trimPattern = patterns.computeIfAbsent(player.getUuid(), uuid -> ItemStackHelper.getRandomTrimPattern(registryManager, random));
        var trimMaterialKey = teamManager.getTeam(player)
                .map(team -> team.getKey().equals(CozyCampfireInstance.TEAM_RED) ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.LAPIS)
                .orElse(ArmorTrimMaterials.IRON);
        var trimMaterial = ItemStackHelper.getTrimMaterial(registryManager, trimMaterialKey);

        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        helmet.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern, false));
        player.equipStack(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = new ItemStack(Items.IRON_CHESTPLATE);
        chestPlate.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        chestPlate.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern, false));
        player.equipStack(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
        leggings.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        leggings.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern, false));
        player.equipStack(EquipmentSlot.LEGS, leggings);

        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        boots.set(DataComponentTypes.TRIM, new ArmorTrim(trimMaterial, trimPattern, false));
        player.equipStack(EquipmentSlot.FEET, boots);
    }
}
