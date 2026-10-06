package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;

public class TextPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.TextPacket packet = (org.cloudburstmc.protocol.bedrock.packet.TextPacket) pk;

        boolean overlay = packet.getType() == org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.TIP || packet.getType() == org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.POPUP
                || packet.getType() == org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.JUKEBOX_POPUP;
        if (packet.isNeedsTranslation()) {
            // The key of a text, with what goes into it
            player.getJavaSession().send(new ClientboundSystemChatPacket(TranslatorUtils.translateText(packet.getMessage(), packet.getParameters()), overlay));
            return;
        }
        if (packet.getType() == org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type.CHAT && packet.getSourceName() != null && !packet.getSourceName().isEmpty()) {
            // Who said it is not in what was said
            player.getJavaSession().send(new ClientboundSystemChatPacket(Component.translatable("chat.type.text", Component.text(packet.getSourceName()), Component.text(packet.getMessage())), false));
            return;
        }

        switch (packet.getType()) {
            case TIP:
            case POPUP: {
                player.sendTip(packet.getMessage());
                break;
            }
            case SYSTEM: {
                player.getJavaSession().send(new ClientboundSystemChatPacket(Component.text(packet.getMessage()), false));
                break;
            }
            default: {
                player.sendMessage(packet.getMessage());
                break;
            }
        }
    }
}
