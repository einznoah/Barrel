package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputMessage;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;

import java.util.Arrays;

// What a server answers to a command
public class CommandOutputPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket packet = (org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket) pk;

        // A bedrock client only shows what a command of a player led to
        if (packet.getCommandOriginData().getOrigin() != CommandOriginType.PLAYER) {
            return;
        }

        for (CommandOutputMessage message : packet.getMessages()) {
            Component text = TranslatorUtils.translateText(message.getMessageId(), Arrays.asList(message.getParameters()));
            // The library calls whether the command did what it was for internal. Some servers do not say it there
            // but with the color of the text
            if (!message.isInternal() && text.color() == null) {
                text = text.color(NamedTextColor.RED);
            }
            player.getJavaSession().send(new ClientboundSystemChatPacket(text, false));
        }
    }
}
