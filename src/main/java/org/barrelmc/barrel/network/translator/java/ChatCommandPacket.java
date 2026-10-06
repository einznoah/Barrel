package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;

import java.util.UUID;

// A command, which a java client sends apart from what is said in the chat, signed when a player is named in it
public class ChatCommandPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        String command = pk instanceof ServerboundChatCommandSignedPacket ? ((ServerboundChatCommandSignedPacket) pk).getCommand() : ((ServerboundChatCommandPacket) pk).getCommand();

        CommandRequestPacket commandRequestPacket = new CommandRequestPacket();
        // A java client leaves the slash out
        commandRequestPacket.setCommand("/" + command);
        commandRequestPacket.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, UUID.randomUUID(), "", player.getUniqueEntityId()));
        commandRequestPacket.setInternal(false);
        player.getBedrockSession().sendPacket(commandRequestPacket);
    }
}
