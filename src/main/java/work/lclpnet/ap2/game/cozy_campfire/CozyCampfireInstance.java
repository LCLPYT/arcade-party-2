package work.lclpnet.ap2.game.cozy_campfire;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.util.ItemStackHelper;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;

import java.util.*;

public class CozyCampfireInstance extends TeamEliminationGameInstance {

    private static final float DAY_TIME_CHANCE = 0.25f, CLEAR_WEATHER_CHANCE = 0.6f, THUNDER_CHANCE = 0.05f;
    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    private final Map<Item, Integer> fuel = new HashMap<>();
    private final Set<Block> breakableBlocks = new HashSet<>();
    private final Random random = new Random();

    public CozyCampfireInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useOldCombat();
        useSurvivalMode();
    }

    @Override
    protected void prepare() {
        setupGameRules();
        randomWorldConditions();

        TeamManager teamManager = getTeamManager();
        teamManager.partitionIntoTeams(gameHandle.getParticipants(), Set.of(TEAM_RED, TEAM_BLUE));

        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        teleportToTeamSpawns();

        registerFuel();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            giveItems(player);
        }
    }

    private void setupGameRules() {
        MinecraftServer server = gameHandle.getServer();

        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.SNOW_ACCUMULATION_HEIGHT).set(0, server);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
    }

    private void randomWorldConditions() {
        ServerWorld world = getWorld();

        if (random.nextFloat() <= DAY_TIME_CHANCE) {
            world.setTimeOfDay(6000);
        } else {
            world.setTimeOfDay(18000);
        }

        if (random.nextFloat() <= CLEAR_WEATHER_CHANCE) {
            world.setWeather(1000, 0, false, false);
        } else {
            boolean thunder = random.nextFloat() <= (THUNDER_CHANCE / CLEAR_WEATHER_CHANCE);  // conditional probability
            world.setWeather(0, 1000, true, thunder);
        }
    }

    private void registerFuel() {
        // vanilla materials
        fuel.putAll(AbstractFurnaceBlockEntity.createFuelTimeMap());

        // custom materials
        addFuel(ItemTags.LEAVES, 50);
        addFuel(ItemTags.BEDS, 400);
        fuel.put(Items.VINE, 40);
        fuel.put(Items.BEEHIVE, 250);
        fuel.put(Items.BOOKSHELF, 400);
        fuel.put(Items.CHISELED_BOOKSHELF, 410);
        fuel.put(Items.TARGET, 160);
        addFuel(ItemTags.WOODEN_DOORS, 350);

        fuel.keySet().stream()
                .map(item -> item instanceof BlockItem bi ? bi : null)
                .filter(Objects::nonNull)
                .map(BlockItem::getBlock)
                .forEach(breakableBlocks::add);

        breakableBlocks.add(Blocks.FIRE);
    }

    private void addFuel(TagKey<Item> tag, int time) {
        for (var entry : Registries.ITEM.iterateEntries(tag)) {
            if (entry.isIn(ItemTags.NON_FLAMMABLE_WOOD)) continue;

            Item item = entry.value();

            fuel.put(item, time);
        }
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.PICKUP_ITEM, ProtectionTypes.SWAP_HAND_ITEMS, ProtectionTypes.PICKUP_PROJECTILE);
            config.disallow(ProtectionTypes.ITEM_FRAME_REMOVE_ITEM);

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, damageSource) -> entity instanceof ServerPlayerEntity);

            config.allow(ProtectionTypes.BREAK_BLOCKS, (entity, pos) -> {
                if (!(entity instanceof ServerPlayerEntity player)) return false;

                return isFuel(player, pos);
            });

            config.allow(ProtectionTypes.BLOCK_ITEM_DROP, (world, blockPos, itemStack) -> isFuel(itemStack));

            config.allow(ProtectionTypes.DROP_ITEM, (playerEntity, i) -> {
                ItemStack toDrop = playerEntity.getInventory().getStack(i);
                System.out.println(toDrop);

                return true;
            });

            config.allow(ProtectionTypes.MODIFY_INVENTORY, clickEvent -> {
                // disable armor interaction
                final int slot = clickEvent.slot();

                if (slot >= 5 && slot <= 8) {
                    return false;
                }

                // prevent dropping non-fuel items
                if (clickEvent.isDropAction()) {
                    ItemStack cursorStack = PlayerUtils.getCursorStack(clickEvent.player());
                    return isFuel(cursorStack);
                }

                return true;
            });
        });
    }

    private boolean isFuel(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = getWorld();
        BlockState state = world.getBlockState(pos);

        if (breakableBlocks.contains(state.getBlock())) return true;

        ItemStack stack = player.getMainHandStack();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, stack)
                .addOptional(LootContextParameters.THIS_ENTITY, player)
                .addOptional(LootContextParameters.BLOCK_ENTITY, blockEntity);

        return state.getDroppedStacks(builder).stream().anyMatch(this::isFuel);
    }

    private boolean isFuel(ItemStack stack) {
        return fuel.containsKey(stack.getItem());
    }

    private void giveItems(ServerPlayerEntity player) {
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

        var trimPattern = ItemStackHelper.getRandomTrimPattern(getWorld().getRegistryManager(), random);
        var trimMaterial = getTeamManager().getTeam(player)
                .map(team -> team.getKey().equals(TEAM_RED) ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.LAPIS)
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
