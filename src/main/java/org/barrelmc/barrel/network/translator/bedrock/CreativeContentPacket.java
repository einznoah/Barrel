package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class CreativeContentPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket) pk;

        player.getInventory().setCreativeItems(packet.getContents(), packet.getGroups());
        // The tabs of the recipe book go by where the items are in the creative inventory
        player.getInventory().sendRecipeBook();
    }
}
