/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DeferredDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.Ingredient;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapedCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapelessCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.CompositeSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.EmptySlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateRecipesPacket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;

// A bedrock server leaves it to the client to find the recipe for what is in a crafting grid or a crafting station
public class CraftingRecipes {

    private static final String CRAFTING_TABLE_TAG = "crafting_table";
    private static final String STONECUTTER_TAG = "stonecutter";
    private static final String JAVA_CRAFTING_TABLE = "minecraft:crafting_table";
    // The data value of an ingredient that can have any
    private static final int ANY_DATA = 32767;

    // The sets of items a java client is told about, it only lets these be put in a smithing table. The other ones
    // are named after the tag of the bedrock recipes they are the ingredients of
    public static final String JAVA_SMITHING_TEMPLATES = "smithing_template";
    public static final String JAVA_SMITHING_BASES = "smithing_base";
    public static final String JAVA_SMITHING_ADDITIONS = "smithing_addition";
    private static final Map<String, String> JAVA_ITEM_SETS = Map.of("furnace", "furnace_input", "blast_furnace", "blast_furnace_input", "smoker", "smoker_input", "campfire", "campfire_input");

    private final List<ShapedRecipeData> shapedRecipes = new ArrayList<>();
    private final List<ShapelessRecipeData> shapelessRecipes = new ArrayList<>();
    private final List<Cut> cuts = new ArrayList<>();
    private final List<SmithingTransformRecipeData> smithingTransforms = new ArrayList<>();
    private final List<SmithingTrimRecipeData> smithingTrims = new ArrayList<>();
    private final Map<String, Set<Integer>> javaItemSets = new HashMap<>();
    // The recipes of the crafting grid by the id the server knows them by, which the java client is told too
    private final Map<Integer, BookRecipe> bookRecipes = new LinkedHashMap<>();

    public record Match(int networkId, List<ItemData> results) {
    }

    // A recipe as the recipe book has it. One that has no shape has no width
    public record BookRecipe(int networkId, String id, int width, int height, List<ItemDescriptorWithCount> ingredients, ItemData result) {
    }

    // What a stonecutter makes of one of the java items
    public record Cut(Set<Integer> javaItemIds, int networkId, ItemData result) {
    }

    // The result is null for a trim, which is the item that is trimmed
    public record Smithing(int networkId, ItemData result) {
    }

    public void setRecipes(List<ShapedRecipeData> shapedRecipes, List<ShapelessRecipeData> shapelessRecipes, List<SmithingTransformRecipeData> smithingTransforms, List<SmithingTrimRecipeData> smithingTrims) {
        this.shapedRecipes.clear();
        this.shapelessRecipes.clear();
        this.cuts.clear();
        this.smithingTransforms.clear();
        this.smithingTrims.clear();
        this.javaItemSets.clear();
        this.bookRecipes.clear();

        for (ShapedRecipeData recipe : shapedRecipes) {
            if (CRAFTING_TABLE_TAG.equals(recipe.getTag()) && !recipe.getResults().isEmpty()) {
                this.shapedRecipes.add(recipe);
                this.bookRecipes.put(recipe.getNetId(), new BookRecipe(recipe.getNetId(), recipe.getId(), recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(), recipe.getResults().get(0)));
            }
        }
        for (ShapelessRecipeData recipe : shapelessRecipes) {
            if (recipe.getResults().isEmpty() || recipe.getTag() == null) {
                continue;
            }

            if (CRAFTING_TABLE_TAG.equals(recipe.getTag())) {
                this.shapelessRecipes.add(recipe);
                this.bookRecipes.put(recipe.getNetId(), new BookRecipe(recipe.getNetId(), recipe.getId(), 0, 0, recipe.getIngredients(), recipe.getResults().get(0)));
            } else if (recipe.getIngredients().size() == 1) {
                Set<Integer> javaItemIds = getJavaItemIds(recipe.getIngredients().get(0));
                if (STONECUTTER_TAG.equals(recipe.getTag()) && !javaItemIds.isEmpty()) {
                    this.cuts.add(new Cut(javaItemIds, recipe.getNetId(), recipe.getResults().get(0)));
                } else if (JAVA_ITEM_SETS.containsKey(recipe.getTag())) {
                    this.getJavaItemSet(JAVA_ITEM_SETS.get(recipe.getTag())).addAll(javaItemIds);
                }
            }
        }

        this.smithingTransforms.addAll(smithingTransforms);
        this.smithingTrims.addAll(smithingTrims);
        for (SmithingTransformRecipeData recipe : smithingTransforms) {
            this.addSmithingItems(recipe.getTemplate(), recipe.getBase(), recipe.getAddition());
        }
        for (SmithingTrimRecipeData recipe : smithingTrims) {
            this.addSmithingItems(recipe.getTemplate(), recipe.getBase(), recipe.getAddition());
        }
    }

    private Set<Integer> getJavaItemSet(String name) {
        return this.javaItemSets.computeIfAbsent(name, key -> new LinkedHashSet<>());
    }

    private void addSmithingItems(ItemDescriptorWithCount template, ItemDescriptorWithCount base, ItemDescriptorWithCount addition) {
        this.getJavaItemSet(JAVA_SMITHING_TEMPLATES).addAll(getJavaItemIds(template));
        this.getJavaItemSet(JAVA_SMITHING_BASES).addAll(getJavaItemIds(base));
        this.getJavaItemSet(JAVA_SMITHING_ADDITIONS).addAll(getJavaItemIds(addition));
    }

    private static Set<Integer> getJavaItemIds(ItemDescriptorWithCount ingredient) {
        Set<Integer> javaItemIds = new LinkedHashSet<>();
        ItemDescriptor descriptor = ingredient.getDescriptor();
        if (descriptor instanceof DefaultDescriptor) {
            DefaultDescriptor defaultDescriptor = (DefaultDescriptor) descriptor;
            if (defaultDescriptor.getItemId() != null) {
                javaItemIds.addAll(ItemConverter.getJavaItemIds(defaultDescriptor.getItemId().getIdentifier(), isAnyData(defaultDescriptor.getAuxValue()) ? null : defaultDescriptor.getAuxValue()));
            }
        } else if (descriptor instanceof ItemTagDescriptor) {
            for (String bedrockName : ItemConverter.BEDROCK_ITEM_TAGS.getOrDefault(((ItemTagDescriptor) descriptor).getItemTag(), Set.of())) {
                javaItemIds.addAll(ItemConverter.getJavaItemIds(bedrockName, null));
            }
        } else if (descriptor instanceof DeferredDescriptor) {
            DeferredDescriptor deferredDescriptor = (DeferredDescriptor) descriptor;
            javaItemIds.addAll(ItemConverter.getJavaItemIds(deferredDescriptor.getFullName(), isAnyData(deferredDescriptor.getAuxValue()) ? null : deferredDescriptor.getAuxValue()));
        }
        return javaItemIds;
    }

    // What a java client is not sent the recipes for: it shows what a stonecutter can make of an item itself, and
    // only lets the items of the smithing recipes be put in a smithing table
    public ClientboundUpdateRecipesPacket toJavaRecipes() {
        Map<Key, int[]> itemSets = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> itemSet : this.javaItemSets.entrySet()) {
            itemSets.put(Key.key(itemSet.getKey()), itemSet.getValue().stream().mapToInt(Integer::intValue).toArray());
        }

        List<ClientboundUpdateRecipesPacket.SelectableRecipe> stonecutterRecipes = new ArrayList<>();
        for (Cut cut : this.cuts) {
            stonecutterRecipes.add(new ClientboundUpdateRecipesPacket.SelectableRecipe(new Ingredient(new HolderSet(new IntArrayList(cut.javaItemIds()))), new ItemStackSlotDisplay(ItemConverter.bedrockToJavaItem(cut.result()))));
        }
        return new ClientboundUpdateRecipesPacket(itemSets, stonecutterRecipes);
    }

    public BookRecipe getBookRecipe(int networkId) {
        return this.bookRecipes.get(networkId);
    }

    public boolean hasBookRecipes() {
        return !this.bookRecipes.isEmpty();
    }

    // The ids the java client knows the recipes with these names by
    public int[] getBookRecipeIds(Collection<String> ids) {
        return this.bookRecipes.values().stream().filter(recipe -> ids.contains(recipe.id())).mapToInt(BookRecipe::networkId).toArray();
    }

    // How a recipe is shown in the recipe book. Returns null if java has none of the items an ingredient can be
    public RecipeDisplay toJavaDisplay(BookRecipe recipe) {
        List<SlotDisplay> ingredients = new ArrayList<>();
        for (ItemDescriptorWithCount ingredient : recipe.ingredients()) {
            Set<Integer> javaItemIds = getJavaItemIds(ingredient);
            if (matches(ingredient, ItemData.AIR)) {
                ingredients.add(EmptySlotDisplay.INSTANCE);
            } else if (javaItemIds.isEmpty()) {
                return null;
            } else if (javaItemIds.size() == 1) {
                ingredients.add(new ItemSlotDisplay(javaItemIds.iterator().next()));
            } else {
                List<SlotDisplay> items = new ArrayList<>();
                for (int javaItemId : javaItemIds) {
                    items.add(new ItemSlotDisplay(javaItemId));
                }
                ingredients.add(new CompositeSlotDisplay(items));
            }
        }

        SlotDisplay result = new ItemStackSlotDisplay(ItemConverter.bedrockToJavaItem(recipe.result()));
        SlotDisplay craftingTable = new ItemSlotDisplay(ItemConverter.getJavaItemId(JAVA_CRAFTING_TABLE));
        if (recipe.width() > 0) {
            return new ShapedCraftingRecipeDisplay(recipe.width(), recipe.height(), ingredients, result, craftingTable);
        }
        ingredients.removeIf(ingredient -> ingredient == EmptySlotDisplay.INSTANCE);
        return new ShapelessCraftingRecipeDisplay(ingredients, result, craftingTable);
    }

    // The recipes of the recipe book: the ones with these names, or all of them without names. The tab a recipe is
    // on is one of java, which bedrock recipes do not have
    public List<ClientboundRecipeBookAddPacket.Entry> toJavaRecipeBook(Collection<String> ids, ToIntFunction<ItemData> javaCategories, boolean notify) {
        List<ClientboundRecipeBookAddPacket.Entry> entries = new ArrayList<>();
        for (BookRecipe recipe : this.bookRecipes.values()) {
            RecipeDisplay display = ids != null && !ids.contains(recipe.id()) ? null : this.toJavaDisplay(recipe);
            if (display == null) {
                continue;
            }

            // What the client checks the inventory of the player for, to show what can be made
            List<HolderSet> requirements = new ArrayList<>();
            for (ItemDescriptorWithCount ingredient : recipe.ingredients()) {
                if (!matches(ingredient, ItemData.AIR)) {
                    requirements.add(new HolderSet(new IntArrayList(getJavaItemIds(ingredient))));
                }
            }
            // The recipes that make the same item are shown as one
            OptionalInt group = OptionalInt.of(ItemConverter.bedrockToJavaItemId(recipe.result()));
            entries.add(new ClientboundRecipeBookAddPacket.Entry(new RecipeDisplayEntry(recipe.networkId(), display, group, javaCategories.applyAsInt(recipe.result()), requirements), notify, notify));
        }
        return entries;
    }

    // What a stonecutter can make of the item, in the order the java client shows it. The client tells which one
    // was picked by its place in this list
    public List<Cut> getCuts(ItemData item) {
        List<Cut> cuts = new ArrayList<>();
        if (!ItemConverter.isEmpty(item)) {
            int javaItemId = ItemConverter.bedrockToJavaItemId(item);
            for (Cut cut : this.cuts) {
                if (cut.javaItemIds().contains(javaItemId)) {
                    cuts.add(cut);
                }
            }
        }
        return cuts;
    }

    // Whether a java client lets the item be put in the slot of a smithing table these items are for
    public boolean isSmithingItem(String javaItemSet, ItemData item) {
        return this.getJavaItemSet(javaItemSet).contains(ItemConverter.bedrockToJavaItemId(item));
    }

    public Smithing findSmithing(ItemData template, ItemData base, ItemData addition) {
        for (SmithingTransformRecipeData recipe : this.smithingTransforms) {
            if (matches(recipe.getTemplate(), template) && matches(recipe.getBase(), base) && matches(recipe.getAddition(), addition)) {
                return new Smithing(recipe.getNetId(), recipe.getResult());
            }
        }
        for (SmithingTrimRecipeData recipe : this.smithingTrims) {
            if (matches(recipe.getTemplate(), template) && matches(recipe.getBase(), base) && matches(recipe.getAddition(), addition)) {
                return new Smithing(recipe.getNetId(), null);
            }
        }
        return null;
    }

    // The grid is given row by row
    public Match find(ItemData[] grid, int gridSize) {
        int firstRow = gridSize, lastRow = -1, firstColumn = gridSize, lastColumn = -1;
        List<ItemData> items = new ArrayList<>();
        for (int slot = 0; slot < grid.length; slot++) {
            if (!ItemConverter.isEmpty(grid[slot])) {
                firstRow = Math.min(firstRow, slot / gridSize);
                lastRow = Math.max(lastRow, slot / gridSize);
                firstColumn = Math.min(firstColumn, slot % gridSize);
                lastColumn = Math.max(lastColumn, slot % gridSize);
                items.add(grid[slot]);
            }
        }
        if (items.isEmpty()) {
            return null;
        }

        // A shaped recipe can be anywhere in the grid, and mirrored
        int width = lastColumn - firstColumn + 1;
        int height = lastRow - firstRow + 1;
        for (ShapedRecipeData recipe : this.shapedRecipes) {
            if (recipe.getWidth() != width || recipe.getHeight() != height || recipe.getIngredients().size() != width * height) {
                continue;
            }

            for (int mirrored = 0; mirrored < 2; mirrored++) {
                boolean matches = true;
                for (int row = 0; row < height && matches; row++) {
                    for (int column = 0; column < width && matches; column++) {
                        ItemData item = grid[(firstRow + row) * gridSize + firstColumn + (mirrored == 1 ? width - 1 - column : column)];
                        matches = matches(recipe.getIngredients().get(row * width + column), item);
                    }
                }
                if (matches) {
                    return new Match(recipe.getNetId(), recipe.getResults());
                }
            }
        }

        for (ShapelessRecipeData recipe : this.shapelessRecipes) {
            if (recipe.getIngredients().size() == items.size() && matchesShapeless(recipe.getIngredients(), items, new boolean[items.size()], 0)) {
                return new Match(recipe.getNetId(), recipe.getResults());
            }
        }
        return null;
    }

    private static boolean matchesShapeless(List<ItemDescriptorWithCount> ingredients, List<ItemData> items, boolean[] used, int ingredient) {
        if (ingredient == ingredients.size()) {
            return true;
        }

        for (int item = 0; item < items.size(); item++) {
            if (!used[item] && matches(ingredients.get(ingredient), items.get(item))) {
                used[item] = true;
                if (matchesShapeless(ingredients, items, used, ingredient + 1)) {
                    return true;
                }
                used[item] = false;
            }
        }
        return false;
    }

    static boolean matches(ItemDescriptorWithCount ingredient, ItemData item) {
        ItemDescriptor descriptor = ingredient.getDescriptor();
        if (descriptor instanceof DefaultDescriptor) {
            // Compared by name, an ingredient the server named instead of numbered comes without the id of its item
            DefaultDescriptor defaultDescriptor = (DefaultDescriptor) descriptor;
            String bedrockName = defaultDescriptor.getItemId() == null ? "" : defaultDescriptor.getItemId().getIdentifier();
            if (bedrockName.isEmpty() || bedrockName.equals("minecraft:air")) {
                return ItemConverter.isEmpty(item);
            }
            return !ItemConverter.isEmpty(item) && bedrockName.equals(item.getDefinition().getIdentifier()) && matchesData(defaultDescriptor.getAuxValue(), item);
        } else if (descriptor instanceof ItemTagDescriptor) {
            return !ItemConverter.isEmpty(item) && ItemConverter.isInBedrockItemTag(item, ((ItemTagDescriptor) descriptor).getItemTag());
        } else if (descriptor instanceof DeferredDescriptor) {
            DeferredDescriptor deferredDescriptor = (DeferredDescriptor) descriptor;
            return !ItemConverter.isEmpty(item) && deferredDescriptor.getFullName().equals(item.getDefinition().getIdentifier()) && matchesData(deferredDescriptor.getAuxValue(), item);
        }

        // An empty spot of a shaped recipe. Molang ingredients are not supported
        return ingredient.getCount() <= 0 && ItemConverter.isEmpty(item);
    }

    private static boolean isAnyData(int data) {
        return data == ANY_DATA || data == -1;
    }

    private static boolean matchesData(int data, ItemData item) {
        return isAnyData(data) || data == item.getDamage();
    }
}
