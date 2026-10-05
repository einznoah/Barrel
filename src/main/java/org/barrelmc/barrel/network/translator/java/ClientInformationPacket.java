package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;

public class ClientInformationPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundClientInformationPacket settingsPacket = (ServerboundClientInformationPacket) pk;

        player.setRenderDistance(settingsPacket.getRenderDistance());
        // The first one arrives before the bedrock server is joined, the chunk radius is requested once it started the game
        if (player.getStartGamePacketCache() == null) {
            return;
        }

        RequestChunkRadiusPacket chunkRadiusPacket = new RequestChunkRadiusPacket();
        chunkRadiusPacket.setRadius(settingsPacket.getRenderDistance());
        chunkRadiusPacket.setMaxRadius(settingsPacket.getRenderDistance());

        player.getBedrockSession().sendPacketImmediately(chunkRadiusPacket);
    }
}
