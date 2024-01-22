package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.game.cozy_campfire.CozyCampfireInstance;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.kibu.inv.item.ItemStackUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CozyCampfireKitManager {

    private final TeamManager teamManager;
    private final ServerWorld world;
    private final Random random;
    private final Map<UUID, RegistryKey<ArmorTrimPattern>> patterns = new HashMap<>();

    public CozyCampfireKitManager(TeamManager teamManager, ServerWorld world, Random random) {
        this.teamManager = teamManager;
        this.world = world;
        this.random = random;
    }

    public void giveItems(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStackUtil.setUnbreakable(sword, true);
        inventory.setStack(0, sword);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStackUtil.setUnbreakable(pickaxe, true);
        inventory.setStack(1, pickaxe);

        ItemStack axe = new ItemStack(Items.IRON_AXE);
        ItemStackUtil.setUnbreakable(axe, true);
        inventory.setStack(2, axe);

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        ItemStackUtil.setUnbreakable(shovel, true);
        inventory.setStack(3, shovel);

        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        ItemStackUtil.setUnbreakable(hoe, true);
        inventory.setStack(4, hoe);

        var trimPattern = patterns.computeIfAbsent(player.getUuid(), uuid -> ItemStackHelper.getRandomTrimPattern(world.getRegistryManager(), random));
        var trimMaterial = teamManager.getTeam(player)
                .map(team -> team.getKey().equals(CozyCampfireInstance.TEAM_RED) ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.LAPIS)
                .orElse(ArmorTrimMaterials.IRON);

        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        ItemStackUtil.setUnbreakable(helmet, true);
        ItemStackHelper.setArmorTrim(helmet, trimPattern, trimMaterial);
        helmet.addHideFlag(ItemStack.TooltipSection.UPGRADES);
        player.equipStack(EquipmentSlot.HEAD, helmet);

        ItemStack chestPlate = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStackUtil.setUnbreakable(chestPlate, true);
        ItemStackHelper.setArmorTrim(chestPlate, trimPattern, trimMaterial);
        chestPlate.addHideFlag(ItemStack.TooltipSection.UPGRADES);
        player.equipStack(EquipmentSlot.CHEST, chestPlate);

        ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
        ItemStackUtil.setUnbreakable(leggings, true);
        ItemStackHelper.setArmorTrim(leggings, trimPattern, trimMaterial);
        leggings.addHideFlag(ItemStack.TooltipSection.UPGRADES);
        player.equipStack(EquipmentSlot.LEGS, leggings);

        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        ItemStackUtil.setUnbreakable(boots, true);
        ItemStackHelper.setArmorTrim(boots, trimPattern, trimMaterial);
        boots.addHideFlag(ItemStack.TooltipSection.UPGRADES);
        player.equipStack(EquipmentSlot.FEET, boots);
    }
}
