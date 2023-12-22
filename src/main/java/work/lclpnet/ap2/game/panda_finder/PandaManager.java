package work.lclpnet.ap2.game.panda_finder;


import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PandaManager {

    private static final int PANDA_COUNT = 100;
    private static final int SEARCHED_PANDA_COUNT = 5;
    private final List<Vec3d> spawns;
    private final Random random;
    private final ServerWorld world;
    private final Set<PandaEntity> pandas = new HashSet<>();
    private PandaEntity.Gene current = null;

    public PandaManager(List<Vec3d> spawns, Random random, ServerWorld world) {
        if (spawns.isEmpty()) throw new IllegalArgumentException("Spawn list is empty");

        this.spawns = spawns;
        this.random = random;
        this.world = world;
    }

    public void next() {
        PandaEntity.Gene[] genes = PandaEntity.Gene.values();
        current = genes[random.nextInt(genes.length)];

        clear();

        populate();
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

            System.out.println(pos);
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
}
