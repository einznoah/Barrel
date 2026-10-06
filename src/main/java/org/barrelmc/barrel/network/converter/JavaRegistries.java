package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.util.HashMap;
import java.util.Map;

// The registries and tags the java client is sent when it joins, they are the ones of a java server without data packs
public class JavaRegistries {

    public static final String BANNER_PATTERN = "minecraft:banner_pattern";
    public static final String ENCHANTMENT = "minecraft:enchantment";
    public static final String ITEM = "minecraft:item";
    public static final String TRIM_MATERIAL = "minecraft:trim_material";
    public static final String TRIM_PATTERN = "minecraft:trim_pattern";

    private static final Map<String, Map<String, Integer>> IDS = new HashMap<>();
    private static final Map<String, Map<Integer, String>> NAMES = new HashMap<>();
    private static NbtMap tags = NbtMap.EMPTY;

    public static void init() {
        NbtMap registries = MinecraftProtocol.loadNetworkCodec();
        for (String registry : registries.keySet()) {
            Map<String, Integer> ids = new HashMap<>();
            Map<Integer, String> names = new HashMap<>();
            for (NbtMap entry : registries.getCompound(registry).getList("value", NbtType.COMPOUND)) {
                ids.put(entry.getString("name"), entry.getInt("id"));
                names.put(entry.getInt("id"), entry.getString("name"));
            }
            IDS.put(registry, ids);
            NAMES.put(registry, names);
        }
        tags = MinecraftProtocol.loadNetworkTags();
    }

    // Returns -1 for what the registry does not have
    public static int getId(String registry, String name) {
        return IDS.getOrDefault(registry, Map.of()).getOrDefault(name, -1);
    }

    public static String getName(String registry, int id) {
        return NAMES.getOrDefault(registry, Map.of()).get(id);
    }

    // A tag is given the way the registries name one, with a # in front. Anything else is not in a tag
    public static boolean isInTag(String registry, String tag, int id) {
        if (tag != null && tag.startsWith("#")) {
            for (int tagId : getTag(registry, tag.substring(1))) {
                if (tagId == id) {
                    return true;
                }
            }
        }
        return false;
    }

    // The ids of a tag, in the order the client has them
    public static int[] getTag(String registry, String tag) {
        int[] ids = tags.getCompound(registry).getIntArray(tag);
        return ids == null ? new int[0] : ids;
    }
}
