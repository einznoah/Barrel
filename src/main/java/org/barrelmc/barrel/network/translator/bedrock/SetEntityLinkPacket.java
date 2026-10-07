package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class SetEntityLinkPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        // An entity gets on another one or off it: a player on a boat, or on a seat an add-on brings
        player.getRiding().link(((org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket) pk).getEntityLink());
    }
}
