package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.ArrayList;
import java.util.List;

public class CraftingDataPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket) pk;

        // A java client is told what a crafting grid makes instead of being sent the recipes, apart from the few it
        // has a use of its own for
        List<ShapedRecipeData> shapedRecipes = new ArrayList<>(packet.getShapedData());
        List<ShapelessRecipeData> shapelessRecipes = new ArrayList<>(packet.getShapelessData());
        // Where the recipes were before the packet had a list for every kind
        for (RecipeData recipe : packet.getCraftingData()) {
            if (recipe instanceof ShapedRecipeData) {
                shapedRecipes.add((ShapedRecipeData) recipe);
            } else if (recipe instanceof ShapelessRecipeData) {
                shapelessRecipes.add((ShapelessRecipeData) recipe);
            }
        }
        player.getInventory().getCraftingRecipes().setRecipes(shapedRecipes, shapelessRecipes, packet.getSmithingTransformData(), packet.getSmithingTrimData());
        player.getJavaSession().send(player.getInventory().getCraftingRecipes().toJavaRecipes());
    }
}
