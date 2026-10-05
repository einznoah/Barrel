package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class ContainerClosePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket packet = (org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket) pk;

        player.getInventory().onBedrockContainerClose(packet.getId(), packet.isServerInitiated());
    }
}
