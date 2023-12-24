package work.lclpnet.ap2.game.panda_finder;


import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.kibu.map.MapUtil;

import java.util.*;

public class PandaManager {

    private static final int PANDA_COUNT = 100;
    private static final int SEARCHED_PANDA_COUNT = 5;
    private final Logger logger;
    private final List<Vec3d> spawns;
    private final Random random;
    private final ServerWorld world;
    private final Participants participants;
    private final Set<PandaEntity> pandas = new HashSet<>();
    private Map<PandaEntity.Gene, List<Integer>> imagesByGene = null;
    private PandaEntity.Gene current = null;
    private int currentMapId = -1;

    public PandaManager(Logger logger, List<Vec3d> spawns, Random random, ServerWorld world, Participants participants) {
        this.logger = logger;
        this.participants = participants;

        if (spawns.isEmpty()) throw new IllegalArgumentException("Spawn list is empty");

        this.spawns = spawns;
        this.random = random;
        this.world = world;
    }

    public void next() {
        PandaEntity.Gene[] genes = PandaEntity.Gene.values();
        current = genes[random.nextInt(genes.length)];

        randomizeImage();

        for (ServerPlayerEntity player : participants) {
            giveImageTo(player);
        }

        clear();

        populate();
    }

    private void randomizeImage() {
        List<Integer> images = imagesByGene.get(current);

        if (images == null || images.isEmpty()) {
            logger.error("There are no images for panda gene {}", current);
            currentMapId = -1;
        } else {
            currentMapId = images.get(random.nextInt(images.size()));
        }
    }

    private void populate() {
        PandaEntity.Gene[] otherGenes = Arrays.stream(PandaEntity.Gene.values())
                .filter(gene -> gene != current)
                .toArray(PandaEntity.Gene[]::new);

        int remain = SEARCHED_PANDA_COUNT;
        final float chance = SEARCHED_PANDA_COUNT / (float) PANDA_COUNT;

        for (int i = 0; i < PANDA_COUNT; i++) {
            Vec3d pos = randomPosition();

            PandaEntity panda = new PandaEntity(EntityType.PANDA, world);

            pandas.add(panda);

            boolean searched = i > PANDA_COUNT - remain - 1 || (remain > 0 && random.nextFloat() < chance);

            PandaEntity.Gene gene;

            if (searched) {
                gene = current;
                remain--;
            } else {
                gene = otherGenes[random.nextInt(otherGenes.length)];
            }

            panda.setMainGene(gene);
            panda.setHiddenGene(gene);

            panda.setBaby(random.nextFloat() < 0.05);

            panda.setPosition(pos);

            world.spawnEntity(panda);
        }
    }

    private Vec3d randomPosition() {
        return spawns.get(random.nextInt(spawns.size()));
    }

    private void clear() {
        for (PandaEntity panda : pandas) {
            panda.discard();
        }
    }

    public boolean isSearchedPanda(PandaEntity panda) {
        return panda.getMainGene() == current;
    }

    public Optional<String> getLocalizedPandaGene() {
        return Optional.ofNullable(current)
                .map(gene -> "game.ap2.panda_finder.find.".concat(gene.asString()));
    }

    public void setFound() {
        current = null;
    }

    public void readImages(JSONObject images) {
        Map<PandaEntity.Gene, List<Integer>> imagesByGene = new HashMap<>();

        for (var key : images.keySet()) {
            var gene = PandaEntity.Gene.CODEC.byId(key);

            if (gene == null) {
                logger.warn("Invalid panda gene named '{}'", key);
                continue;
            }

            JSONArray array = images.getJSONArray(key);
            List<Integer> ids = new ArrayList<>(array.length());

            for (Object o : array) {
                if (!(o instanceof Number number)) {
                    logger.warn("Invalid integer value '{}'", o);
                    continue;
                }

                ids.add(number.intValue());
            }

            imagesByGene.put(gene, ids);
        }

        this.imagesByGene = imagesByGene;
    }

    public void giveImageTo(ServerPlayerEntity player) {
        if (currentMapId == -1) {
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            return;
        }

        ItemStack filledMap = new ItemStack(Items.FILLED_MAP);
        MapUtil.setMapId(filledMap, currentMapId);

        player.setStackInHand(Hand.OFF_HAND, filledMap);
    }
}
