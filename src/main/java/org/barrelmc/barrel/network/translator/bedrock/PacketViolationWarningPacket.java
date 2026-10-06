package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;

public class PacketViolationWarningPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket) pk;

        // The server did not take a packet of the proxy, and tells which one and what is wrong with it
        BedrockPacketDefinition<?> definition = ProxyServer.getInstance().getBedrockPacketCodec().getPacketDefinition(packet.getPacketCauseId());
        String packetName = definition == null ? "an unknown packet" : definition.getFactory().get().getClass().getSimpleName();
        String warning = "The bedrock server did not accept " + packetName + " (id " + packet.getPacketCauseId() + "): " + packet.getType() + ", " + packet.getSeverity() + ", " + packet.getContext();
        System.out.println(warning + " [player " + player.getJavaUsername() + "]");
        player.getJavaSession().send(new ClientboundSystemChatPacket(Component.text("§c" + warning), false));
    }

    @Override
    public boolean immediate() {
        // The server closes the connection right after
        return true;
    }
}
