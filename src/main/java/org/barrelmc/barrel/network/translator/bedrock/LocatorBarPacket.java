package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// Where the other players are, for the dots on the bar above the hotbar
public class LocatorBarPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket) pk;
        for (org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Payload payload : packet.getWaypoints()) {
            player.getWaypoints().update(payload);
        }
    }
}
