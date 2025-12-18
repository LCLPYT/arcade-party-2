package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import java.util.Random;
import java.util.stream.Stream;

public class GoatHornItemClass implements ItemClass {

    private final RegistryAccess registryManager;

    public GoatHornItemClass(RegistryAccess registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public ItemStack getRandomStack(Random random) {
        var subRandom = net.minecraft.util.RandomSource.create(random.nextLong());
        TagKey<Instrument> tagKey = InstrumentTags.GOAT_HORNS;

        return registryManager
                .lookupOrThrow(Registries.INSTRUMENT)
                .getRandomElementOf(tagKey, subRandom)
                .map(instrument -> InstrumentItem.create(Items.GOAT_HORN, instrument))
                .orElseGet(() -> new ItemStack(Items.GOAT_HORN));
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.GOAT_HORN);
    }
}
