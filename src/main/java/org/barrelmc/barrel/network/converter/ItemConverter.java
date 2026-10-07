/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.barrelmc.barrel.utils.FileManager;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ArmorTrim;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.MobEffectDetails;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.MobEffectInstance;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.PotionContents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public class ItemConverter {

    // Keyed by the bedrock item name, followed by its data for the items that need it to be told apart
    public static final HashMap<String, Integer> BEDROCK_ITEM_TO_JAVA_ITEM = new HashMap<>();
    public static final HashMap<Integer, String> JAVA_ITEM_TO_BEDROCK_ITEM = new HashMap<>();
    public static final HashMap<Integer, Integer> JAVA_ITEM_MAX_STACK_SIZE = new HashMap<>();
    // How much damage the java items that wear out can take
    public static final HashMap<Integer, Integer> JAVA_ITEM_MAX_DAMAGE = new HashMap<>();
    // What an anvil mends the java items with, a tag of items or the items themselves
    private static final HashMap<Integer, String> JAVA_ITEM_REPAIR_TAGS = new HashMap<>();
    private static final HashMap<Integer, Set<Integer>> JAVA_ITEM_REPAIR_ITEMS = new HashMap<>();
    // How long the java items that are eaten or drunk take to be
    public static final HashMap<Integer, Integer> JAVA_ITEM_CONSUME_TICKS = new HashMap<>();
    private static final HashMap<String, Integer> JAVA_ITEM_IDS = new HashMap<>();
    private static final HashMap<Integer, String> JAVA_ITEM_NAMES = new HashMap<>();
    // All the java items of a bedrock item, keyed like the bedrock items above and by the name alone
    private static final HashMap<String, List<Integer>> BEDROCK_ITEM_TO_JAVA_ITEMS = new HashMap<>();
    // The data of these bedrock items is the potion they hold
    private static final Set<String> BEDROCK_POTIONS = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion");
    // An arrow is tipped with the potion its data is one more than
    private static final String BEDROCK_ARROW = "minecraft:arrow";
    private static final String JAVA_TIPPED_ARROW = "minecraft:tipped_arrow";
    private static final List<String> BEDROCK_FUEL_TAGS = List.of("minecraft:coals", "minecraft:logs_that_burn", "minecraft:planks", "minecraft:wooden_slabs");
    private static final Set<String> BEDROCK_FUELS = Set.of("minecraft:coal_block", "minecraft:lava_bucket", "minecraft:blaze_rod", "minecraft:dried_kelp_block", "minecraft:stick", "minecraft:bamboo");
    // The bedrock items of the item tags, a recipe can ask for any item of a tag
    public static final HashMap<String, Set<String>> BEDROCK_ITEM_TAGS = new HashMap<>();
    private static final String BUNDLE_ID = "bundle_id";
    // A bundle can hold bundles. Should the numbers of bundles ever name each other, this is where it ends
    private static final int MAX_BUNDLES_IN_BUNDLES = 8;

    // Shown for the bedrock items java does not have
    private static int unknownJavaItem = 0;

    public static void init() {
        JsonObject jsonObject = FileManager.getJsonObjectFromResource("runtime_items.json");

        assert jsonObject != null;

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            Integer javaItemId = Integer.valueOf(entry.getKey());
            JsonObject itemEntry = entry.getValue().getAsJsonObject();
            String javaName = itemEntry.get("java_name").getAsString();
            String bedrockName = itemEntry.get("bedrock_name").getAsString();
            String bedrockItem = itemEntry.has("bedrock_data") ? bedrockName + ":" + itemEntry.get("bedrock_data").getAsInt() : bedrockName;

            JAVA_ITEM_TO_BEDROCK_ITEM.put(javaItemId, bedrockItem);
            JAVA_ITEM_IDS.put(javaName, javaItemId);
            JAVA_ITEM_NAMES.put(javaItemId, javaName);
            BEDROCK_ITEM_TO_JAVA_ITEMS.computeIfAbsent(bedrockName, key -> new ArrayList<>()).add(javaItemId);
            if (itemEntry.has("bedrock_data")) {
                BEDROCK_ITEM_TO_JAVA_ITEMS.computeIfAbsent(bedrockItem, key -> new ArrayList<>()).add(javaItemId);
            }
            // A few java items are the same item on bedrock, the one with the same name is the closest
            if (javaName.equals(bedrockName)) {
                BEDROCK_ITEM_TO_JAVA_ITEM.put(bedrockItem, javaItemId);
                BEDROCK_ITEM_TO_JAVA_ITEM.put(bedrockName, javaItemId);
            } else {
                BEDROCK_ITEM_TO_JAVA_ITEM.putIfAbsent(bedrockItem, javaItemId);
                BEDROCK_ITEM_TO_JAVA_ITEM.putIfAbsent(bedrockName, javaItemId);
            }
            if (itemEntry.has("max_stack_size")) {
                JAVA_ITEM_MAX_STACK_SIZE.put(javaItemId, itemEntry.get("max_stack_size").getAsInt());
            }
            if (itemEntry.has("max_damage")) {
                JAVA_ITEM_MAX_DAMAGE.put(javaItemId, itemEntry.get("max_damage").getAsInt());
            }
            if (itemEntry.has("repair_tag")) {
                JAVA_ITEM_REPAIR_TAGS.put(javaItemId, "#" + itemEntry.get("repair_tag").getAsString());
            }
            if (itemEntry.has("repair_items")) {
                Set<Integer> repairItems = new HashSet<>();
                for (JsonElement repairItem : itemEntry.get("repair_items").getAsJsonArray()) {
                    repairItems.add(repairItem.getAsInt());
                }
                JAVA_ITEM_REPAIR_ITEMS.put(javaItemId, repairItems);
            }
            if (itemEntry.has("consume_ticks")) {
                JAVA_ITEM_CONSUME_TICKS.put(javaItemId, itemEntry.get("consume_ticks").getAsInt());
            }
            if (javaName.equals("minecraft:barrier")) {
                unknownJavaItem = javaItemId;
            }
        }

        jsonObject = FileManager.getJsonObjectFromResource("bedrock_item_tags.json");

        assert jsonObject != null;

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            Set<String> bedrockItems = new HashSet<>();
            for (JsonElement bedrockItem : entry.getValue().getAsJsonArray()) {
                bedrockItems.add(bedrockItem.getAsString());
            }
            BEDROCK_ITEM_TAGS.put(entry.getKey(), bedrockItems);
        }
    }

    // What a shift click puts in the fuel slot of a furnace. Only the common fuels, a furnace burns much more than these
    public static boolean isBedrockFuel(ItemData item) {
        for (String itemTag : BEDROCK_FUEL_TAGS) {
            if (isInBedrockItemTag(item, itemTag)) {
                return true;
            }
        }
        return BEDROCK_FUELS.contains(item.getDefinition().getIdentifier());
    }

    public static boolean isInBedrockItemTag(ItemData item, String itemTag) {
        Set<String> bedrockItems = BEDROCK_ITEM_TAGS.get(itemTag);
        return bedrockItems != null && bedrockItems.contains(item.getDefinition().getIdentifier());
    }

    public static boolean isEmpty(ItemData item) {
        return item == null || item.isNull() || item.getCount() <= 0;
    }

    public static int bedrockToJavaItemId(ItemData item) {
        String bedrockName = item.getDefinition().getIdentifier();
        if (bedrockName.equals(BEDROCK_ARROW) && item.getDamage() > 0) {
            return JAVA_ITEM_IDS.get(JAVA_TIPPED_ARROW);
        }

        Integer javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName + ":" + item.getDamage());
        if (javaItemId == null) {
            javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName);
        }

        return javaItemId == null ? unknownJavaItem : javaItemId;
    }

    // The name of the java item a bedrock item is, null for one that is not known. What a block holds is told to a
    // java client with the names of the items
    public static String getJavaItemName(String bedrockName, int bedrockData) {
        Integer javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName + ":" + bedrockData);
        if (javaItemId == null) {
            javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName);
        }
        return javaItemId == null ? null : JAVA_ITEM_NAMES.get(javaItemId);
    }

    public static int getJavaItemId(String javaName) {
        return JAVA_ITEM_IDS.get(javaName);
    }

    // The java items an ingredient of a bedrock recipe can be, whatever its data is if it is not given
    public static List<Integer> getJavaItemIds(String bedrockName, Integer bedrockData) {
        List<Integer> javaItemIds = bedrockData == null ? null : BEDROCK_ITEM_TO_JAVA_ITEMS.get(bedrockName + ":" + bedrockData);
        if (javaItemIds == null) {
            javaItemIds = BEDROCK_ITEM_TO_JAVA_ITEMS.get(bedrockName);
        }
        return javaItemIds == null ? new ArrayList<>() : javaItemIds;
    }

    // Name of the bedrock item, followed by its data if several java items share it
    public static String javaToBedrockItem(int javaItemId) {
        return JAVA_ITEM_TO_BEDROCK_ITEM.get(javaItemId);
    }

    // The same for an item that can hold a potion, which is the data of the bedrock item. Returns null for a potion
    // bedrock does not have
    public static String javaToBedrockItem(ItemStack javaItem) {
        String bedrockItem = javaToBedrockItem(javaItem.getId());
        PotionContents potionContents = javaItem.getDataComponentsPatch() == null ? null : javaItem.getDataComponentsPatch().get(DataComponentTypes.POTION_CONTENTS);
        boolean tippedArrow = javaItem.getId() == JAVA_ITEM_IDS.get(JAVA_TIPPED_ARROW);
        if (potionContents == null || !(tippedArrow || BEDROCK_POTIONS.contains(bedrockItem))) {
            return bedrockItem;
        }

        int bedrockPotionId = PotionConverter.javaToBedrockPotionId(potionContents.getPotionId());
        if (bedrockPotionId == -1) {
            return null;
        }
        return tippedArrow ? BEDROCK_ARROW + ":" + (bedrockPotionId + 1) : bedrockItem + ":" + bedrockPotionId;
    }

    public static int getMaxStackSize(ItemData item) {
        return JAVA_ITEM_MAX_STACK_SIZE.getOrDefault(bedrockToJavaItemId(item), 64);
    }

    // How much damage the item can take, 0 for the items that do not wear out
    public static int getMaxDamage(ItemData item) {
        return JAVA_ITEM_MAX_DAMAGE.getOrDefault(bedrockToJavaItemId(item), 0);
    }

    // Whether an anvil mends the item with the material
    public static boolean isRepairMaterial(ItemData item, ItemData material) {
        int javaItemId = bedrockToJavaItemId(item);
        int javaMaterialId = bedrockToJavaItemId(material);
        return JavaRegistries.isInTag(JavaRegistries.ITEM, JAVA_ITEM_REPAIR_TAGS.get(javaItemId), javaMaterialId) || JAVA_ITEM_REPAIR_ITEMS.getOrDefault(javaItemId, Set.of()).contains(javaMaterialId);
    }

    // The ticks it takes to eat or drink the item, 0 for the items that are used in another way
    public static int getConsumeTicks(ItemData item) {
        return JAVA_ITEM_CONSUME_TICKS.getOrDefault(bedrockToJavaItemId(item), 0);
    }

    // Returns null for a potion java does not have, which a java client shows as an uncraftable potion
    private static PotionContents getPotionContents(int bedrockPotionId) {
        if (bedrockPotionId == PotionConverter.BEDROCK_DECAY_POTION) {
            // Not a potion of java, but java can make a potion of any effect
            MobEffectInstance wither = new MobEffectInstance(Effect.WITHER, new MobEffectDetails(1, PotionConverter.DECAY_TICKS, false, true, true, null));
            return new PotionContents(-1, PotionConverter.DECAY_COLOR, new ArrayList<>(List.of(wither)), null);
        }

        int javaPotionId = PotionConverter.bedrockToJavaPotionId(bedrockPotionId);
        return javaPotionId == -1 ? null : new PotionContents(javaPotionId, -1, new ArrayList<>(), null);
    }

    // The java item a thrown potion is shown as
    public static ItemStack getJavaPotion(String javaName, int bedrockPotionId) {
        DataComponents components = new DataComponents(new HashMap<>());
        PotionContents potionContents = getPotionContents(bedrockPotionId);
        if (potionContents != null) {
            components.put(DataComponentTypes.POTION_CONTENTS, potionContents);
        }
        return new ItemStack(JAVA_ITEM_IDS.get(javaName), 1, components);
    }

    public static boolean isBundle(ItemData item) {
        return !isEmpty(item) && item.getDefinition().getIdentifier().endsWith("bundle");
    }

    // The number a bedrock server has for what is in a bundle, or null for an item that has none
    public static Integer getBundleId(ItemData item) {
        return isBundle(item) && item.getTag() != null && item.getTag().containsKey(BUNDLE_ID, NbtType.INT) ? (Integer) item.getTag().getInt(BUNDLE_ID) : null;
    }

    public static ItemStack bedrockToJavaItem(ItemData item) {
        return bedrockToJavaItem(item, null);
    }

    // A bedrock server sends what is in a bundle apart from the bundle, a java client has it with the item. With
    // what the bundles hold by their numbers, a bundle is told with what is in it
    public static ItemStack bedrockToJavaItem(ItemData item, IntFunction<ItemData[]> bundles) {
        return bedrockToJavaItem(item, bundles, 0);
    }

    private static ItemStack bedrockToJavaItem(ItemData item, IntFunction<ItemData[]> bundles, int bundlesAround) {
        if (isEmpty(item)) {
            return null;
        }

        int javaItemId = bedrockToJavaItemId(item);
        DataComponents components = new DataComponents(new HashMap<>());
        if (javaItemId == unknownJavaItem && !item.getDefinition().getIdentifier().equals("minecraft:barrier")) {
            components.put(DataComponentTypes.CUSTOM_NAME, Component.text(item.getDefinition().getIdentifier()).decoration(TextDecoration.ITALIC, false));
        }

        String bedrockName = item.getDefinition().getIdentifier();
        if (BEDROCK_POTIONS.contains(bedrockName) || (bedrockName.equals(BEDROCK_ARROW) && item.getDamage() > 0)) {
            PotionContents potionContents = getPotionContents(bedrockName.equals(BEDROCK_ARROW) ? item.getDamage() - 1 : item.getDamage());
            if (potionContents != null) {
                components.put(DataComponentTypes.POTION_CONTENTS, potionContents);
            }
        }

        NbtMap tag = item.getTag();
        if (tag != null) {
            if (tag.containsKey("Damage", NbtType.INT)) {
                components.put(DataComponentTypes.DAMAGE, tag.getInt("Damage"));
            }
            if (tag.containsKey("ench", NbtType.LIST)) {
                Map<Integer, Integer> enchantments = EnchantmentConverter.bedrockToJavaEnchantments(tag);
                if (enchantments.isEmpty()) {
                    // Enchanted with something java does not have
                    components.put(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                } else {
                    // An enchanted book holds its enchantments instead of being enchanted with them
                    boolean stored = item.getDefinition().getIdentifier().equals("minecraft:enchanted_book");
                    components.put(stored ? DataComponentTypes.STORED_ENCHANTMENTS : DataComponentTypes.ENCHANTMENTS, new ItemEnchantments(enchantments));
                }
            }

            if (tag.containsKey("Patterns", NbtType.LIST)) {
                components.put(DataComponentTypes.BANNER_PATTERNS, BannerConverter.bedrockToJavaPatterns(tag));
            }
            NbtMap trim = tag.getCompound("Trim", null);
            if (trim != null) {
                int javaMaterialId = JavaRegistries.getId(JavaRegistries.TRIM_MATERIAL, "minecraft:" + trim.getString("Material"));
                int javaPatternId = JavaRegistries.getId(JavaRegistries.TRIM_PATTERN, "minecraft:" + trim.getString("Pattern"));
                if (javaMaterialId != -1 && javaPatternId != -1) {
                    components.put(DataComponentTypes.TRIM, new ArmorTrim(Holder.ofId(javaMaterialId), Holder.ofId(javaPatternId)));
                }
            }
            if (tag.containsKey("map_uuid", NbtType.LONG)) {
                // What is on a map is not translated, this only tells the client that the item is a map that was drawn
                components.put(DataComponentTypes.MAP_ID, (int) tag.getLong("map_uuid"));
            }

            NbtMap display = tag.getCompound("display", null);
            if (display != null) {
                if (display.containsKey("Name", NbtType.STRING)) {
                    components.put(DataComponentTypes.CUSTOM_NAME, Component.text(display.getString("Name")).decoration(TextDecoration.ITALIC, false));
                }
                if (display.containsKey("Lore", NbtType.LIST)) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : display.getList("Lore", NbtType.STRING)) {
                        lore.add(Component.text(line));
                    }
                    components.put(DataComponentTypes.LORE, lore);
                }
            }
        }

        Integer bundleId = bundles == null || bundlesAround > MAX_BUNDLES_IN_BUNDLES ? null : getBundleId(item);
        ItemData[] bundle = bundleId == null ? null : bundles.apply(bundleId);
        if (bundle != null) {
            // A java client has what was put in last in front, a bedrock server has it at the end
            List<ItemStack> contents = new ArrayList<>();
            for (int slot = bundle.length - 1; slot >= 0; slot--) {
                ItemStack content = bedrockToJavaItem(bundle[slot], bundles, bundlesAround + 1);
                if (content != null) {
                    contents.add(content);
                }
            }
            components.put(DataComponentTypes.BUNDLE_CONTENTS, contents);
        }

        return new ItemStack(javaItemId, item.getCount(), components);
    }
}
