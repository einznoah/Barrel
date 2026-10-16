package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// The game mode of a player changes. A server of mojang tells it this way also for the player itself
public class UpdatePlayerGameTypePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket) pk;

        if (packet.getEntityId() == player.getUniqueEntityId() && packet.getGameType() != null) {
            TranslatorUtils.changeGameMode(player, packet.getGameType());
        }
    }
}
