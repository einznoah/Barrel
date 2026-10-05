package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class NetworkSettingsPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket packet = (org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket) pk;

        player.getBedrockSession().setCompression(packet.getCompressionAlgorithm());

        if (ProxyServer.getInstance().getConfig().getAuth().equals("offline")) {
            player.getBedrockSession().sendPacketImmediately(player.getLoginPacket());
        } else {
            try {
                player.getBedrockSession().sendPacketImmediately(player.getOnlineLoginPacket());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
