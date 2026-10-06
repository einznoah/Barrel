package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.ArrayList;

public class UnlockedRecipesPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket) pk;

        player.getInventory().setUnlockedRecipes(packet.getAction(), new ArrayList<>(packet.getUnlockedRecipes()));
    }
}
