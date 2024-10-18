package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.hook.LivingEntityAttributeInitCallback;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;

public class MSManager {

    private static final int MOB_COUNT = 3;
    private final ServerWorld world;
    private final MSStruct struct;
    private final Participants participants;
    private final Logger logger;
    private final MSTargetManager targetManager;
    private final int mapChunkRadius;

    public MSManager(ServerWorld world, GameMap map, MSStruct struct, Participants participants, Logger logger) {
        this.world = world;
        this.struct = struct;
        this.participants = participants;
        this.logger = logger;

        targetManager = new MSTargetManager(struct, participants);
        mapChunkRadius = MSGenerator.getMaxChunkSize(map);
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(LivingEntityAttributeInitCallback.HOOK, this::initAttributes);
    }

    public void spawnMobs() {
        // find most distant nodes where the monsters may spawn
        var spawns = mostDistantSpawns();

        if (spawns.size() < 3) {
            logger.error("Failed to find {} spawn points for mobs", MOB_COUNT);
            return;
        }

        spawnWarden(spawns.getFirst());
    }

    private List<Vec3d> mostDistantSpawns() {
        var playerNodes = playerNodes();
        var distanceCalculator = struct.distanceCalculator();

        var nodes = struct.graph().nodes();
        Object2IntMap<Object> distances = new Object2IntOpenHashMap<>(nodes.size());

        for (var node : nodes) {
            int minDistance = Integer.MAX_VALUE;

            for (var playerNode : playerNodes) {
                int distance = distanceCalculator.distance(playerNode, node);

                if (distance < minDistance) {
                    minDistance = distance;
                }
            }

            if (minDistance == Integer.MAX_VALUE) continue;

            distances.put(node, minDistance);
        }

        nodes.sort(Comparator.comparingInt(node -> distances.getOrDefault(node, Integer.MIN_VALUE)).reversed());

        return nodes.stream()
                .flatMap(node -> Optional.ofNullable(node.oriented())
                        .map(OrientedStructurePiece::spawn)
                        .stream())
                .limit(MOB_COUNT)
                .toList();
    }

    private Set<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes() {
        Set<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> playerNodes = new HashSet<>();

        for (ServerPlayerEntity player : participants) {
            var node = struct.nodeAt(player.getPos());

            if (node != null) {
                playerNodes.add(node);
            }
        }

        return playerNodes;
    }

    private void spawnWarden(Vec3d pos) {
        WardenEntity warden = new WardenEntity(EntityType.WARDEN, world);
        warden.setPosition(pos);
        warden.setInvulnerable(true);
        warden.setPersistent();
        warden.setOnGround(true);  // required to perform path finding immediately

        // TODO adjust ai for mini game
        var brain = warden.getBrain();
        brain.remember(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, Ticks.minutes(15));
        brain.remember(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, Ticks.minutes(15));

        EntityNavigation navigation = warden.getNavigation();

        if (navigation instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanWalkOverFences(true);
            nav.setCanEnterOpenDoors(true);
        }

        world.spawnEntity(warden);

        ServerPlayerEntity nearest = targetManager.findNearestTarget(warden);

        if (nearest != null) {
            targetManager.assignTarget(warden, nearest);
        }
    }

    private void initAttributes(LivingEntity living) {
        if (!isMonster(living)) return;

        // make sure monsters can track down players everywhere in the map
        EntityUtil.setAttribute(living, EntityAttributes.GENERIC_FOLLOW_RANGE, 2 * mapChunkRadius * 16);
    }

    private boolean isMonster(LivingEntity living) {
        return world == living.getWorld() && (living instanceof WardenEntity);
    }
}
