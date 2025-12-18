package work.lclpnet.ap2.impl.resource;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionfc;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.model.TemplateModel;
import work.lclpnet.gaco.scene.Object3d;
import work.lclpnet.gaco.scene.Scene;
import work.lclpnet.gaco.scene.VoidMountContext;
import work.lclpnet.gaco.scene.object.BlockDisplayObject;
import work.lclpnet.gaco.scene.object.ItemDisplayObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelLoader {

    private static final Pattern SUMMON_PATTERN = Pattern.compile("(?:execute at @[sp] run )?summon (?:[a-z0-9_.-]+:)?[a-z0-9/._-]+ (~|~?[-+\\d.]+) (~|~?[-+\\d.]+) (~|~?[-+\\d.]+) ");
    private final HolderLookup.Provider lookup;
    private final HolderLookup.RegistryLookup<Block> blockLookup;
    private final Scene scene = new Scene(VoidMountContext.INSTANCE);

    public ModelLoader(HolderLookup.Provider lookup) {
        this.lookup = lookup;
        blockLookup = lookup.lookupOrThrow(Registries.BLOCK);
    }

    @Nullable
    public TemplateModel load(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String str = new String(bytes, StandardCharsets.UTF_8);

        Matcher matcher = SUMMON_PATTERN.matcher(str);

        Object3d root = null;
        int count = 0;

        while (matcher.find()) {
            String xs = matcher.group(1);
            String ys = matcher.group(2);
            String zs = matcher.group(3);

            double x = coordinate(xs);
            double y = coordinate(ys);
            double z = coordinate(zs);

            int index = matcher.end();
            String snbt = str.substring(index);

            CompoundTag nbt;

            try {
                nbt = TagParser.parseCompoundFully(snbt);
            } catch (CommandSyntaxException e) {
                throw new IOException("Failed to parse model nbt", e);
            }

            var obj = new Object3d(scene);
            obj.position.set(x, y, z);

            parseChildren(obj, nbt);

            if (root == null) {
                root = obj;
            } else {
                if (count == 1) {
                    var newRoot = new Object3d(scene);
                    newRoot.addChild(root);
                    root = newRoot;
                }

                root.addChild(obj);
            }

            count++;
        }

        if (root == null) {
            return null;
        }

        root.updateMatrixWorld();

        return new TemplateModel(root);
    }

    private double coordinate(String s) {
        int len = s.length();

        if (len == 0) {
            return 0;
        }

        boolean rel = s.charAt(0) == '~';

        if (len == 1 && rel) {
            return 0;
        }

        return Double.parseDouble(s.substring(rel ? 1 : 0));
    }

    private void parseChildren(Object3d root, CompoundTag nbt) {
        ListTag passengers = nbt.getListOrEmpty("Passengers");

        for (Tag passenger : passengers) {
            if (passenger instanceof CompoundTag compound) {
                parseChild(root, compound);
            }
        }
    }

    private void parseChild(Object3d root, CompoundTag nbt) {
        String id = nbt.getStringOr("id", null);

        Object3d obj = switch (id) {
            case "minecraft:block_display" -> {
                BlockState state = NbtUtils.readBlockState(blockLookup, nbt.getCompoundOrEmpty("block_state"));
                yield new BlockDisplayObject(scene, state);
            }
            case "minecraft:item_display" -> {
                var stack = ItemHelper.fromNbt(lookup, nbt.getCompoundOrEmpty("item")).orElse(ItemStack.EMPTY);
                yield new ItemDisplayObject(scene, stack);
            }
            case null, default -> null;
        };

        if (obj == null) return;

        var transformation = Transformation.EXTENDED_CODEC.decode(NbtOps.INSTANCE, nbt.get("transformation"))
                .result().map(Pair::getFirst)
                .orElse(Transformation.identity());

        obj.scale.set(transformation.getScale());
        obj.position.set(transformation.getTranslation());

        // rotation = leftRotation * rightRotation
        Quaternionfc right = transformation.getRightRotation();
        obj.rotation.set(transformation.getLeftRotation()).mul(right.x(), right.y(), right.z(), right.w());

        root.addChild(obj);
    }
}
