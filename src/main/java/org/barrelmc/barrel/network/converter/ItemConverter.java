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
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemConverter {

    // Keyed by the bedrock item name, followed by its data for the items that need it to be told apart
    public static final HashMap<String, Integer> BEDROCK_ITEM_TO_JAVA_ITEM = new HashMap<>();
    public static final HashMap<Integer, String> JAVA_ITEM_TO_BEDROCK_ITEM = new HashMap<>();
    public static final HashMap<Integer, Integer> JAVA_ITEM_MAX_STACK_SIZE = new HashMap<>();

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
            if (javaName.equals("minecraft:barrier")) {
                unknownJavaItem = javaItemId;
            }
        }
    }

    public static boolean isEmpty(ItemData item) {
        return item == null || item.isNull() || item.getCount() <= 0;
    }

    public static int bedrockToJavaItemId(ItemData item) {
        String bedrockName = item.getDefinition().getIdentifier();
        Integer javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName + ":" + item.getDamage());
        if (javaItemId == null) {
            javaItemId = BEDROCK_ITEM_TO_JAVA_ITEM.get(bedrockName);
        }

        return javaItemId == null ? unknownJavaItem : javaItemId;
    }

    // Name of the bedrock item, followed by its data if several java items share it
    public static String javaToBedrockItem(int javaItemId) {
        return JAVA_ITEM_TO_BEDROCK_ITEM.get(javaItemId);
    }

    public static int getMaxStackSize(ItemData item) {
        return JAVA_ITEM_MAX_STACK_SIZE.getOrDefault(bedrockToJavaItemId(item), 64);
    }

    public static ItemStack bedrockToJavaItem(ItemData item) {
        if (isEmpty(item)) {
            return null;
        }

        int javaItemId = bedrockToJavaItemId(item);
        DataComponents components = new DataComponents(new HashMap<>());
        if (javaItemId == unknownJavaItem && !item.getDefinition().getIdentifier().equals("minecraft:barrier")) {
            components.put(DataComponentTypes.CUSTOM_NAME, Component.text(item.getDefinition().getIdentifier()).decoration(TextDecoration.ITALIC, false));
        }

        NbtMap tag = item.getTag();
        if (tag != null) {
            if (tag.containsKey("Damage", NbtType.INT)) {
                components.put(DataComponentTypes.DAMAGE, tag.getInt("Damage"));
            }
            if (tag.containsKey("ench", NbtType.LIST)) {
                components.put(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
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

        return new ItemStack(javaItemId, item.getCount(), components);
    }
}
