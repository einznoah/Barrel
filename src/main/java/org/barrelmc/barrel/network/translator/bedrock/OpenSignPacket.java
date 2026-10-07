package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundOpenSignEditorPacket;

// The player has put a sign down or clicked on one, and may write on it
public class OpenSignPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket packet = (org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket) pk;

        player.getJavaSession().send(new ClientboundOpenSignEditorPacket(packet.getPosition(), packet.isFrontSide()));
    }
}
