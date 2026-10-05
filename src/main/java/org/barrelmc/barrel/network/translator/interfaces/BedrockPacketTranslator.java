package org.barrelmc.barrel.network.translator.interfaces;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.barrelmc.barrel.player.Player;

public interface BedrockPacketTranslator {

    default boolean immediate() {
        return false;
    }

    void translate(BedrockPacket pk, Player player);
}
