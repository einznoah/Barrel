package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class CorrectPlayerMovePredictionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket) pk;

        // What the player steers is moved by the server, see Riding: where the server has it is told with it, and
        // there is nothing to put right for the java client
        if (packet.getPredictionType() == PredictionType.PLAYER) {
            player.getInput().correct(packet.getPosition(), packet.isOnGround(), packet.getTick());
        }
    }
}
