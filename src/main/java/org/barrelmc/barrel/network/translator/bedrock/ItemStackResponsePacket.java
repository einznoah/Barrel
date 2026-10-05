package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class ItemStackResponsePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket packet = (org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket) pk;

        for (ItemStackResponse response : packet.getEntries()) {
            player.getInventory().onResponse(response);
        }
    }
}
