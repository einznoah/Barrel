package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class InventoryContentPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket) pk;

        player.getInventory().setContents(packet.getContainerId(), packet.getContents());
    }
}
