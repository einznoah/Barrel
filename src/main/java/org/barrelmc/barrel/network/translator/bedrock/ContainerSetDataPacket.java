package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class ContainerSetDataPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket) pk;

        player.getInventory().setContainerData(packet.getWindowId(), packet.getProperty(), packet.getValue());
    }
}
