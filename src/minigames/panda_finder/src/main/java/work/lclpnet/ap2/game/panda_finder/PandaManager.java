package work.lclpnet.ap2.game.panda_finder;


import com.mojang.serialization.JavaOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec3;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;

import java.util.*;

public class PandaManager {

    private static final int PANDA_COUNT = 100;
    private static final int SEARCHED_PANDA_COUNT = 5;
    private final Logger logger;
    private final List<Vec3> spawns;
    private final Random random;
    private final ServerLevel world;
    private final Participants participants;
    private final Set<Panda> pandas = new HashSet<>();
    private Map<Panda.Gene, List<Integer>> imagesByGene = null;
    private Panda.Gene current = null;
    private int currentMapId = -1;

    public PandaManager(Logger logger, List<Vec3> spawns, Random random, ServerLevel world, Participants participants) {
        this.logger = logger;
        this.participants = participants;

        if (spawns.isEmpty()) throw new IllegalArgumentException("Spawn list is empty");

        this.spawns = spawns;
        this.random = random;
        this.world = world;
    }

    public void next() {
        Panda.Gene[] genes = Panda.Gene.values();
        current = genes[random.nextInt(genes.length)];

        randomizeImage();

        for (ServerPlayer player : participants) {
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
        Panda.Gene[] otherGenes = Arrays.stream(Panda.Gene.values())
                .filter(gene -> gene != current)
                .toArray(Panda.Gene[]::new);

        int remain = SEARCHED_PANDA_COUNT;
        final float chance = SEARCHED_PANDA_COUNT / (float) PANDA_COUNT;

        for (int i = 0; i < PANDA_COUNT; i++) {
            Vec3 pos = randomPosition();

            Panda panda = new Panda(EntityType.PANDA, world);

            pandas.add(panda);

            boolean searched = i > PANDA_COUNT - remain - 1 || (remain > 0 && random.nextFloat() < chance);

            Panda.Gene gene;

            if (searched) {
                gene = current;
                remain--;
            } else {
                gene = otherGenes[random.nextInt(otherGenes.length)];
            }

            panda.setMainGene(gene);
            panda.setHiddenGene(gene);

            panda.setBaby(random.nextFloat() < 0.05);

            panda.setPos(pos);

            world.addFreshEntity(panda);
        }
    }

    private Vec3 randomPosition() {
        return spawns.get(random.nextInt(spawns.size()));
    }

    private void clear() {
        for (Panda panda : pandas) {
            panda.discard();
        }
    }

    public boolean isSearchedPanda(Panda panda) {
        return panda.getMainGene() == current;
    }

    public Optional<String> getLocalizedPandaGene() {
        return Optional.ofNullable(current)
                .map(gene -> "game.ap2.panda_finder.find.".concat(gene.getSerializedName()));
    }

    public synchronized void setFound() {
        for (Panda panda : pandas) {
            if (isSearchedPanda(panda)) {
                panda.setGlowingTag(true);
            }
        }

        current = null;
    }

    public void readImages(JSONObject images) {
        Map<Panda.Gene, List<Integer>> imagesByGene = new HashMap<>();

        for (String key : images.keySet()) {
            var gene = Panda.Gene.CODEC.parse(JavaOps.INSTANCE, key).result().orElse(null);

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

    public void giveImageTo(ServerPlayer player) {
        if (currentMapId == -1) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return;
        }

        ItemStack filledMap = new ItemStack(Items.FILLED_MAP);
        filledMap.set(DataComponents.MAP_ID, new MapId(currentMapId));

        player.setItemInHand(InteractionHand.OFF_HAND, filledMap);
    }
}
