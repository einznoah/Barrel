package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class ContainerOpenPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket) pk;

        // A java client opens its own inventory without the server
        if (packet.getType() != ContainerType.INVENTORY) {
            player.getInventory().openContainer(packet.getId(), packet.getType());
        } else {
            player.getInventory().onBedrockInventoryOpen(packet.getId());
        }
    }
}
