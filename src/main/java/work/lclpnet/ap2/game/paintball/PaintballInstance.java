package work.lclpnet.ap2.game.paintball;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.paintball.kit.RifleKit;
import work.lclpnet.ap2.game.paintball.util.*;
import work.lclpnet.ap2.impl.game.TeamGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.game.kit.KitHandler;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.ServerWorldMountContext;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.TickMovementObserver;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor;

public class PaintballInstance extends TeamGameInstance implements MapBootstrapFunction {

    private final IntScoreDataContainer<Team, TeamRef> data = new IntScoreDataContainer<>(this::createReference, Ordering.DESCENDING, "game.ap2.paintball.blocks_painted");
    private final Random random = new Random();
    private final TickMovementObserver movementObserver;

    private PaintManager paintManager;
    private KitHandler kitHandler;
    private PaintballTeams teams;
    private ResetBlockWorldModifier baseWalls;
    private PaintGunManager paintGunManager;

    public PaintballInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        var collisionDetector = new ChunkedCollisionDetector();
        movementObserver = new TickMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);

        getTeamManager().setUseColorCodes(true);
    }

    @Override
    protected DataContainer<Team, TeamRef> getData() {
        return data;
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        teams = new PaintballTeams(getTeamManager(), map, random, gameHandle.getLogger());
        teams.setup();

        Scene scene = new Scene(new ServerWorldMountContext(world));
        scene.animate(1, gameHandle.getGameScheduler());

        BlockShape bounds = MapUtil.readShape(map, "bounds");

        paintManager = new PaintManager(world, teams, getTeamManager(), data, bounds);
        paintGunManager = new PaintGunManager(world, scene, paintManager, teams, random, gameHandle.getParticipants(), winManager::isGameOver);
        paintGunManager.init(gameHandle.getHookRegistrar());

        replaceTemplateColors(world);
        closeBases(world);

        paintManager.countBlocks();
    }

    private void replaceTemplateColors(ServerWorld world) {
        for (PaintballTeam team : teams) {
            DyeTeamKey template = team.templateColor();

            for (BlockPos pos : team.baseBounds()) {
                BlockState state = world.getBlockState(pos);
                Paintable paintable = paintManager.paintable(state.getBlock());

                if (paintable == null || !state.isOf(paintable.blockFor(template))) continue;

                paintManager.replace(pos, state, paintable, team.key());
            }
        }
    }

    @Override
    protected void prepare() {
        getTeamManager()
                .partitionIntoTeams(gameHandle.getParticipants(), teams.stream()
                .map(PaintballTeam::key)
                .collect(Collectors.toSet()));

        movementObserver.init(gameHandle.getGameScheduler(), gameHandle.getHookRegistrar(), gameHandle.getServer());

        teleportTeamsToSpawns();
        equipPlayers();
        setupKits();
    }

    private void setupKits() {
        kitHandler = KitHandler.create(gameHandle, getWorld(), kitHandle -> List.of(
                new RifleKit(kitHandle, paintGunManager)
        ));

        kitHandler.setup();

        for (PaintballTeam pbt : teams) {
            movementObserver.whenEntering(pbt.baseBounds(), player -> {
                if (teams.isMember(pbt, player)) {
                    kitHandler.enableKitChanger(player);
                }
            });

            movementObserver.whenLeaving(pbt.baseBounds(), player -> {
                if (teams.isMember(pbt, player)) {
                    kitHandler.disableKitChanger(player);
                }
            });
        }
    }

    private void equipPlayers() {
        for (PaintballTeam instance : teams) {
            Team team = getTeamManager().getTeam(instance.key()).orElse(null);

            if (team == null) continue;

            int color = instance.key().color();

            for (ServerPlayerEntity player : team.getPlayers()) {
                player.equipStack(EquipmentSlot.HEAD, getLeatherArmor(Items.LEATHER_HELMET, color));
                player.equipStack(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, color));
                player.equipStack(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, color));
                player.equipStack(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, color));
            }
        }
    }

    @Override
    protected void afterInitialDelay() {
        kitHandler.startKitSelectionTimer(commons(), super::afterInitialDelay);
    }

    @Override
    protected void ready() {
        openBases();

        kitHandler.closeKitChanger();
        kitHandler.selectKitItem();
    }

    private void closeBases(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE;

        baseWalls = new ResetBlockWorldModifier(world, flags);

        for (PaintballTeam team : teams) {
            BlockBox bounds = team.baseBounds();

            for (BlockPos pos : bounds) {
                if (!bounds.isBorder(pos)) continue;

                BlockState state = world.getBlockState(pos);

                if (!state.getCollisionShape(world, pos).isEmpty()) continue;

                baseWalls.setBlockState(pos, Blocks.BARRIER.getDefaultState(), flags);
            }
        }
    }

    private void openBases() {
        if (baseWalls != null) {
            baseWalls.undo();
        }
    }

    @Override
    protected void teleportTeamsToSpawns() {
        ServerWorld world = getWorld();

        for (PaintballTeam pbt : teams) {
            Team team = getTeamManager().getTeam(pbt.key()).orElse(null);

            if (team == null) continue;

            Vec3d pos = pbt.spawn();

            for (ServerPlayerEntity player : team.getPlayers()) {
                player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), pbt.yaw(), 0, true);
            }
        }
    }
}
