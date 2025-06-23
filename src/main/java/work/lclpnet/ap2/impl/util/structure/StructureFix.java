package work.lclpnet.ap2.impl.util.structure;

import com.google.gson.JsonParseException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import org.slf4j.Logger;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.jnbt.ListTag;
import work.lclpnet.kibu.jnbt.StringTag;
import work.lclpnet.kibu.jnbt.Tag;
import work.lclpnet.kibu.mc.BuiltinKibuBlockEntity;
import work.lclpnet.kibu.mc.KibuBlockEntity;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.nbt.FabricNbtConversion;
import work.lclpnet.kibu.structure.BlockEntityStorage;
import work.lclpnet.kibu.structure.BlockStructure;

public class StructureFix {

    private final RegistryWrapper.WrapperLookup registries;
    private final Logger logger;

    public StructureFix(RegistryWrapper.WrapperLookup registries, Logger logger) {
        this.registries = registries;
        this.logger = logger;
    }

    public BlockStructure fixStructure(BlockStructure structure) {
        fixBlockEntities(structure);

        return structure;
    }

    private void fixBlockEntities(BlockStructure structure) {
        KibuBlockPos start = structure.getOrigin(), end = start.add(structure.getWidth() - 1, structure.getHeight() - 1, structure.getLength() - 1);

        for (KibuBlockPos pos : KibuBlockPos.iterateCuboid(start, end)) {
            KibuBlockEntity blockEntity = structure.getBlockEntity(pos);

            if (blockEntity == null) continue;

            String id = blockEntity.getId();

            switch (id) {
                case "minecraft:sign", "minecraft:hanging_sign" -> fixSign(structure, pos, blockEntity);
                case null, default -> {}
            }
        }
    }

    private void fixSign(BlockEntityStorage storage, KibuBlockPos pos, KibuBlockEntity sign) {
        CompoundTag nbt = sign.createNbt();

        boolean changedFront = fixSignMessages(nbt.getCompound("front_text"));
        boolean changedBack = fixSignMessages(nbt.getCompound("back_text"));

        if (!changedFront && !changedBack) return;

        var newSign = new BuiltinKibuBlockEntity(sign.getId(), sign.getPosition(), nbt);

        storage.setBlockEntity(pos, newSign);
    }

    private boolean fixSignMessages(CompoundTag signText) {
        ListTag messages = signText.getList("messages");
        ListTag fixed = new ListTag(StringTag.class);

        boolean changed = false;

        for (Tag tag : messages) {
            Tag converted = convertStringToTag(tag);

            fixed.add(converted);

            if (converted == tag) continue;

            changed = true;
        }

        if (changed) {
            signText.put("messages", fixed);
        }

        return changed;
    }

    private Tag convertStringToTag(Tag tag) {
        if (!(tag instanceof StringTag stringTag)) {
            return tag;
        }

        String json = stringTag.getValue();

        MutableText text;

        try {
            text = Text.Serialization.fromJson(json, registries);
        } catch (JsonParseException ignored) {
            return tag;
        }

        var nbtElement = TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, text)
                .resultOrPartial(err -> logger.error("Failed to encode {} as nbt element: {}", text, err))
                .orElse(null);

        if (nbtElement == null) {
            return tag;
        }

        return FabricNbtConversion.convert(nbtElement);
    }
}
