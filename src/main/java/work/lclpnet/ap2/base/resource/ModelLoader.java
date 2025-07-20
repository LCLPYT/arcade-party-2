package work.lclpnet.ap2.base.resource;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.AffineTransformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.object.BlockDisplayObject;
import work.lclpnet.ap2.impl.scene.object.ItemDisplayObject;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.model.TemplateModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelLoader {

    private static final Pattern SUMMON_PATTERN = Pattern.compile("(?:execute at @[sp] run )?summon (?:[a-z0-9_.-]+:)?[a-z0-9/._-]+ (~|~?[-+\\d.]+) (~|~?[-+\\d.]+) (~|~?[-+\\d.]+) ");
    private final RegistryWrapper.WrapperLookup lookup;
    private final RegistryWrapper.Impl<Block> blockLookup;

    public ModelLoader(RegistryWrapper.WrapperLookup lookup) {
        this.lookup = lookup;
        blockLookup = lookup.getOrThrow(RegistryKeys.BLOCK);
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

            NbtCompound nbt;

            try {
                nbt = StringNbtReader.readCompound(snbt);
            } catch (CommandSyntaxException e) {
                throw new IOException("Failed to parse model nbt", e);
            }

            var obj = new Object3d();
            obj.position.set(x, y, z);

            parseChildren(obj, nbt);

            if (root == null) {
                root = obj;
            } else {
                if (count == 1) {
                    var newRoot = new Object3d();
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

    private void parseChildren(Object3d root, NbtCompound nbt) {
        NbtList passengers = nbt.getListOrEmpty("Passengers");

        for (NbtElement passenger : passengers) {
            if (passenger instanceof NbtCompound compound) {
                parseChild(root, compound);
            }
        }
    }

    private void parseChild(Object3d root, NbtCompound nbt) {
        String id = nbt.getString("id", null);

        Object3d obj = switch (id) {
            case "minecraft:block_display" -> {
                BlockState state = NbtHelper.toBlockState(blockLookup, nbt.getCompoundOrEmpty("block_state"));
                yield new BlockDisplayObject(state);
            }
            case "minecraft:item_display" -> {
                var stack = ItemHelper.fromNbt(lookup, nbt.getCompoundOrEmpty("item")).orElse(ItemStack.EMPTY);
                yield new ItemDisplayObject(stack);
            }
            case null, default -> null;
        };

        if (obj == null) return;

        var transformation = AffineTransformation.ANY_CODEC.decode(NbtOps.INSTANCE, nbt.get("transformation"))
                .result().map(Pair::getFirst)
                .orElse(AffineTransformation.identity());

        obj.scale.set(transformation.getScale());
        obj.position.set(transformation.getTranslation());

        // rotation = leftRotation * rightRotation
        Quaternionf right = transformation.getRightRotation();
        obj.rotation.set(transformation.getLeftRotation()).mul(right.x, right.y, right.z, right.w);

        root.addChild(obj);
    }
}
