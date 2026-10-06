package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.EnchantmentConverter;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Map;
import java.util.Set;

// What the crafting stations make. A bedrock server does not send it, a bedrock client works it out the same way.
// How damaged what an anvil or a grindstone made is, is also in the answer of the server
public class Workstations {

    // The bedrock ids of the enchantments a grindstone leaves on an item
    private static final Set<Integer> BEDROCK_CURSES = Set.of(27, 28);

    private static final String TRIM_TEMPLATE = "_armor_trim_smithing_template";
    // What the items an armor is trimmed with are called in the trim
    private static final Map<String, String> TRIM_MATERIALS = Map.ofEntries(
            Map.entry("minecraft:quartz", "quartz"), Map.entry("minecraft:iron_ingot", "iron"), Map.entry("minecraft:netherite_ingot", "netherite"),
            Map.entry("minecraft:redstone", "redstone"), Map.entry("minecraft:copper_ingot", "copper"), Map.entry("minecraft:gold_ingot", "gold"),
            Map.entry("minecraft:emerald", "emerald"), Map.entry("minecraft:diamond", "diamond"), Map.entry("minecraft:lapis_lazuli", "lapis"),
            Map.entry("minecraft:amethyst_shard", "amethyst"), Map.entry("minecraft:resin_brick", "resin")
    );

    // What an anvil makes, the levels it takes for it and how many of the second item it uses up
    public record Forged(ItemData result, int cost, int materials) {
    }

    private static boolean isItem(ItemData item, String bedrockName) {
        return !ItemConverter.isEmpty(item) && item.getDefinition().getIdentifier().equals(bedrockName);
    }

    // The levels an anvil takes on top for an item it worked on before
    private static int getRepairCost(ItemData item) {
        return ItemConverter.isEmpty(item) || item.getTag() == null ? 0 : item.getTag().getInt("RepairCost");
    }

    private static int getDamage(ItemData item) {
        return item.getTag() == null ? 0 : item.getTag().getInt("Damage");
    }

    public static String getCustomName(ItemData item) {
        NbtMap display = item.getTag() == null ? null : item.getTag().getCompound("display", null);
        return display != null && display.containsKey("Name", NbtType.STRING) ? display.getString("Name") : "";
    }

    // An anvil mends an item with what it is made of, makes one of two of a kind, puts the enchantments of a book
    // on it and names it. Returns null if it has nothing to do. What an item is made of is not known here, the
    // server refuses the wrong material
    public static Forged forge(ItemData input, ItemData material, String name) {
        if (ItemConverter.isEmpty(input)) {
            return null;
        }

        NbtMapBuilder tag = (input.getTag() == null ? NbtMap.EMPTY : input.getTag()).toBuilder();
        int work = 0;
        int materials = 0;
        if (!ItemConverter.isEmpty(material)) {
            boolean enchantedBook = isItem(material, "minecraft:enchanted_book");
            int maxDamage = ItemConverter.getMaxDamage(input);
            int damage = getDamage(input);
            if (!enchantedBook && material.getDefinition().getRuntimeId() != input.getDefinition().getRuntimeId()) {
                // Every piece of the material mends a quarter of what the item can take
                while (maxDamage > 0 && damage > 0 && materials < material.getCount()) {
                    damage -= Math.min(damage, maxDamage / 4);
                    materials++;
                    work++;
                }
                if (materials == 0) {
                    return null;
                }
            } else {
                materials = 1;
                if (!enchantedBook && maxDamage > 0) {
                    // Two of a kind are as good as both of them together, and a little better
                    int combinedDamage = Math.max(0, maxDamage - ((maxDamage - damage) + (maxDamage - getDamage(material)) + maxDamage * 12 / 100) + 1);
                    if (combinedDamage < damage) {
                        damage = combinedDamage;
                        work += 2;
                    }
                }

                Map<Integer, Integer> enchantments = EnchantmentConverter.getEnchantments(input.getTag());
                for (Map.Entry<Integer, Integer> enchantment : EnchantmentConverter.getEnchantments(material.getTag()).entrySet()) {
                    int level = enchantments.getOrDefault(enchantment.getKey(), 0);
                    int addedLevel = Math.min(level == enchantment.getValue() ? level + 1 : Math.max(level, enchantment.getValue()), EnchantmentConverter.getMaxLevel(enchantment.getKey()));
                    int levelCost = EnchantmentConverter.getAnvilCost(enchantment.getKey());
                    work += (enchantedBook ? Math.max(1, levelCost / 2) : levelCost) * Math.max(0, addedLevel - level);
                    enchantments.put(enchantment.getKey(), addedLevel);
                }
                if (!enchantments.isEmpty()) {
                    tag = EnchantmentConverter.setEnchantments(tag.build(), enchantments).toBuilder();
                }
            }
            if (damage != getDamage(input)) {
                tag.putInt("Damage", damage);
            }
        }

        boolean worked = work > 0;
        if (!name.equals(getCustomName(input))) {
            // Without a name the item loses the one it was given before
            NbtMapBuilder display = (input.getTag() == null ? NbtMap.EMPTY : input.getTag()).getCompound("display", NbtMap.EMPTY).toBuilder();
            if (name.isEmpty()) {
                display.remove("Name");
            } else {
                display.putString("Name", name);
            }
            if (display.isEmpty()) {
                tag.remove("display");
            } else {
                tag.putCompound("display", display.build());
            }
            work++;
        }
        if (work == 0) {
            return null;
        }

        int cost = work + getRepairCost(input) + getRepairCost(material);
        if (worked) {
            // Working on the item again takes more levels, naming it does not
            tag.putInt("RepairCost", Math.max(getRepairCost(input), getRepairCost(material)) * 2 + 1);
        }
        return new Forged(input.toBuilder().tag(tag.isEmpty() ? null : tag.build()).build(), cost, materials);
    }

    // A smithing table makes another item of an item, which keeps what it was enchanted with and how damaged it is
    public static ItemData transform(ItemData base, ItemData result) {
        return result.toBuilder().tag(base.getTag()).count(1).build();
    }

    // Returns null if the items are not what an armor is trimmed with
    public static ItemData trim(ItemData base, ItemData template, ItemData material) {
        String bedrockTemplate = template.getDefinition().getIdentifier();
        String trimMaterial = TRIM_MATERIALS.get(material.getDefinition().getIdentifier());
        if (trimMaterial == null || !bedrockTemplate.endsWith(TRIM_TEMPLATE)) {
            return null;
        }

        String trimPattern = bedrockTemplate.substring(bedrockTemplate.indexOf(':') + 1, bedrockTemplate.length() - TRIM_TEMPLATE.length());
        NbtMap tag = base.getTag() == null ? NbtMap.EMPTY : base.getTag();
        NbtMap trim = NbtMap.builder().putString("Material", trimMaterial).putString("Pattern", trimPattern).build();
        return base.toBuilder().tag(tag.toBuilder().putCompound("Trim", trim).build()).count(1).build();
    }

    // A grindstone takes the enchantments off an item, and makes one item of two of a kind. How damaged that one is
    // is not worked out here, the server tells. Returns null for what a grindstone has no use for
    public static ItemData grind(ItemData input, ItemData additional, ItemDefinition book) {
        if (ItemConverter.isEmpty(input)) {
            input = additional;
            additional = ItemData.AIR;
        }
        boolean two = !ItemConverter.isEmpty(additional);
        if (ItemConverter.isEmpty(input) || (two && input.getDefinition().getRuntimeId() != additional.getDefinition().getRuntimeId())) {
            return null;
        }

        Map<Integer, Integer> enchantments = EnchantmentConverter.getEnchantments(input.getTag());
        boolean enchantedBook = isItem(input, "minecraft:enchanted_book");
        if ((!two && enchantments.isEmpty()) || (two && enchantedBook)) {
            return null;
        }

        enchantments.keySet().removeIf(enchantment -> !BEDROCK_CURSES.contains(enchantment));
        NbtMapBuilder tagBuilder = (input.getTag() == null ? NbtMap.EMPTY : input.getTag()).toBuilder();
        tagBuilder.remove("ench");
        tagBuilder.remove("RepairCost");
        NbtMap tag = enchantments.isEmpty() ? tagBuilder.build() : EnchantmentConverter.setEnchantments(tagBuilder.build(), enchantments);

        ItemData.Builder result = input.toBuilder().tag(tag.isEmpty() ? null : tag).count(two ? 1 : input.getCount());
        if (enchantedBook && enchantments.isEmpty() && book != null) {
            result.definition(book);
        }
        return result.build();
    }

    // A cartography table copies a map with an empty map, and makes it larger with paper or locks it with a glass
    // pane. The map looks the same as an item, what changes about it is only known to the server
    public static ItemData drawMap(ItemData map, ItemData additional) {
        if (!isItem(map, "minecraft:filled_map")) {
            return null;
        }

        if (isItem(additional, "minecraft:empty_map")) {
            return map.toBuilder().count(2).build();
        } else if (isItem(additional, "minecraft:paper") || isItem(additional, "minecraft:glass_pane")) {
            return map.toBuilder().count(1).build();
        }
        return null;
    }
}
