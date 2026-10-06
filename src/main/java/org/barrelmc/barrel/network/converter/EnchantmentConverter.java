package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnchantmentConverter {

    // The java names of the enchantments in the order of their bedrock ids
    private static final String[] BEDROCK_ENCHANTMENTS = {
            "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "thorns",
            "respiration", "depth_strider", "aqua_affinity", "sharpness", "smite", "bane_of_arthropods", "knockback",
            "fire_aspect", "looting", "efficiency", "silk_touch", "unbreaking", "fortune", "power", "punch", "flame",
            "infinity", "luck_of_the_sea", "lure", "frost_walker", "mending", "binding_curse", "vanishing_curse",
            "impaling", "riptide", "loyalty", "channeling", "multishot", "piercing", "quick_charge", "soul_speed",
            "swift_sneak", "wind_burst", "density", "breach", "lunge"
    };

    // Indexed by the bedrock id, -1 for an enchantment java does not have
    private static final int[] JAVA_ENCHANTMENT_IDS = new int[BEDROCK_ENCHANTMENTS.length];
    private static final int[] MAX_LEVELS = new int[BEDROCK_ENCHANTMENTS.length];
    // The levels an anvil takes for every level of the enchantment it adds
    private static final int[] ANVIL_COSTS = new int[BEDROCK_ENCHANTMENTS.length];

    public static void init() {
        // The registry the java client is sent when it joins
        Map<String, NbtMap> javaEnchantments = new HashMap<>();
        for (NbtMap entry : MinecraftProtocol.loadNetworkCodec().getCompound("minecraft:enchantment").getList("value", NbtType.COMPOUND)) {
            javaEnchantments.put(entry.getString("name"), entry);
        }

        for (int bedrockId = 0; bedrockId < BEDROCK_ENCHANTMENTS.length; bedrockId++) {
            NbtMap entry = javaEnchantments.get("minecraft:" + BEDROCK_ENCHANTMENTS[bedrockId]);
            JAVA_ENCHANTMENT_IDS[bedrockId] = entry == null ? -1 : entry.getInt("id");
            MAX_LEVELS[bedrockId] = entry == null ? 1 : entry.getCompound("element").getInt("max_level", 1);
            ANVIL_COSTS[bedrockId] = entry == null ? 1 : entry.getCompound("element").getInt("anvil_cost", 1);
        }
    }

    public static int bedrockToJavaEnchantmentId(int bedrockId) {
        return bedrockId >= 0 && bedrockId < JAVA_ENCHANTMENT_IDS.length ? JAVA_ENCHANTMENT_IDS[bedrockId] : -1;
    }

    public static int getMaxLevel(int bedrockId) {
        return bedrockId >= 0 && bedrockId < MAX_LEVELS.length ? MAX_LEVELS[bedrockId] : 1;
    }

    public static int getAnvilCost(int bedrockId) {
        return bedrockId >= 0 && bedrockId < ANVIL_COSTS.length ? ANVIL_COSTS[bedrockId] : 1;
    }

    // The enchantments of a bedrock item by their bedrock ids, in the order the item has them
    public static Map<Integer, Integer> getEnchantments(NbtMap tag) {
        Map<Integer, Integer> enchantments = new LinkedHashMap<>();
        if (tag != null && tag.containsKey("ench", NbtType.LIST)) {
            for (Object enchantment : tag.getList("ench", NbtType.COMPOUND, new ArrayList<>())) {
                enchantments.put((int) ((NbtMap) enchantment).getShort("id"), (int) ((NbtMap) enchantment).getShort("lvl"));
            }
        }
        return enchantments;
    }

    public static NbtMap setEnchantments(NbtMap tag, Map<Integer, Integer> enchantments) {
        List<NbtMap> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> enchantment : enchantments.entrySet()) {
            list.add(NbtMap.builder().putShort("id", enchantment.getKey().shortValue()).putShort("lvl", enchantment.getValue().shortValue()).build());
        }
        return (tag == null ? NbtMap.EMPTY : tag).toBuilder().putList("ench", NbtType.COMPOUND, list).build();
    }

    // Keyed by the ids the java client knows the enchantments by
    public static Map<Integer, Integer> bedrockToJavaEnchantments(NbtMap tag) {
        Map<Integer, Integer> javaEnchantments = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> enchantment : getEnchantments(tag).entrySet()) {
            int javaId = bedrockToJavaEnchantmentId(enchantment.getKey());
            if (javaId != -1) {
                javaEnchantments.put(javaId, enchantment.getValue());
            }
        }
        return javaEnchantments;
    }
}
