package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class PlayerActionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket) pk;

        // The server has brought the player to another dimension and sent what is there. A bedrock client answers
        // with the same and closes its loading screen
        if (packet.getAction() == PlayerActionType.DIMENSION_CHANGE_SUCCESS) {
            ChangeDimensionPacket.answer(player);
            ChangeDimensionPacket.finish(player, false);
        }
    }
}
