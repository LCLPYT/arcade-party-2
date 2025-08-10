package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.InstrumentTags;
import net.minecraft.registry.tag.TagKey;

import java.util.Random;
import java.util.stream.Stream;

public class GoatHornItemClass implements ItemClass {

    private final DynamicRegistryManager registryManager;

    public GoatHornItemClass(DynamicRegistryManager registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public ItemStack getRandomStack(Random random) {
        var subRandom = net.minecraft.util.math.random.Random.create(random.nextLong());
        TagKey<Instrument> tagKey = InstrumentTags.GOAT_HORNS;

        return registryManager
                .getOrThrow(RegistryKeys.INSTRUMENT)
                .getRandomEntry(tagKey, subRandom)
                .map(instrument -> GoatHornItem.getStackForInstrument(Items.GOAT_HORN, instrument))
                .orElseGet(() -> new ItemStack(Items.GOAT_HORN));
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.GOAT_HORN);
    }
}
