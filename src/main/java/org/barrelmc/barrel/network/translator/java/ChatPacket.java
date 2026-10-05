package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

public class ChatPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundChatPacket chatPacket = (ServerboundChatPacket) pk;
        TextPacket textPacket = new TextPacket();

        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setNeedsTranslation(false);
        textPacket.setSourceName(player.getUsername());
        textPacket.setMessage(chatPacket.getMessage());
        textPacket.setXuid(player.getXuid());
        textPacket.setPlatformChatId("");
        textPacket.setFilteredMessage("");

        player.getBedrockSession().sendPacket(textPacket);
    }
}
