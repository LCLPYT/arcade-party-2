package work.lclpnet.ap2.impl.game.item;

import org.json.JSONObject;
import work.lclpnet.ap2.impl.ds.WeightedList;

import java.util.HashMap;
import java.util.Map;

public class SpecialItemRegistry implements SpecialItemRegistrar {

    private final Map<String, Entry> items = new HashMap<>();

    @Override
    public SpecialItemRegistrar register(SpecialItem item, float chance) {
        if (chance < 0) throw new IllegalArgumentException("Chance cannot be negative");

        items.put(item.id(), new Entry(item, chance));

        return this;
    }

    public WeightedList<SpecialItem> weightedItems(JSONObject overrides) {
        var weighted = new WeightedList<SpecialItem>(items.size());

        for (var registered : items.entrySet()) {
            String key = registered.getKey();
            Entry entry = registered.getValue();
            float chance = overrides.optFloat(key);

            if (Float.isNaN(chance)) {
                chance = entry.chance;
            }

            if (chance > 0) {
                weighted.add(entry.item, entry.chance);
            }
        }

        return weighted;
    }

    private record Entry(SpecialItem item, float chance) {}
}
