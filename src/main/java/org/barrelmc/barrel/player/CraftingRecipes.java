/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.ItemConverter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DeferredDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;

import java.util.ArrayList;
import java.util.List;

// A bedrock server leaves it to the client to find the recipe for what is in a crafting grid
public class CraftingRecipes {

    private static final String CRAFTING_TABLE_TAG = "crafting_table";
    // The data value of an ingredient that can have any
    private static final int ANY_DATA = 32767;

    private final List<ShapedRecipeData> shapedRecipes = new ArrayList<>();
    private final List<ShapelessRecipeData> shapelessRecipes = new ArrayList<>();

    public record Match(int networkId, List<ItemData> results) {
    }

    public void setRecipes(List<ShapedRecipeData> shapedRecipes, List<ShapelessRecipeData> shapelessRecipes) {
        this.shapedRecipes.clear();
        this.shapelessRecipes.clear();

        for (ShapedRecipeData recipe : shapedRecipes) {
            if (CRAFTING_TABLE_TAG.equals(recipe.getTag()) && !recipe.getResults().isEmpty()) {
                this.shapedRecipes.add(recipe);
            }
        }
        for (ShapelessRecipeData recipe : shapelessRecipes) {
            if (CRAFTING_TABLE_TAG.equals(recipe.getTag()) && !recipe.getResults().isEmpty()) {
                this.shapelessRecipes.add(recipe);
            }
        }
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

    private static boolean matches(ItemDescriptorWithCount ingredient, ItemData item) {
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

    private static boolean matchesData(int data, ItemData item) {
        return data == ANY_DATA || data == -1 || data == item.getDamage();
    }
}
