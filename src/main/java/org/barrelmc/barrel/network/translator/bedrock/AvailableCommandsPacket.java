package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;

import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

// The commands a player may use. A java client marks a command it was not told about as wrong
public class AvailableCommandsPacket implements BedrockPacketTranslator {

    private static final int ROOT = 0;
    private static final int ARGUMENTS = 1;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket) pk;

        Set<String> names = new TreeSet<>();
        for (CommandData command : packet.getCommands()) {
            names.add(command.getName());
            if (command.getAliases() != null) {
                names.addAll(command.getAliases().getValues().keySet());
            }
        }

        // TODO: What a command takes. The client is told the names, and that anything may follow one
        CommandNode[] nodes = new CommandNode[names.size() + 2];
        int[] commands = new int[names.size()];
        int index = ARGUMENTS + 1;
        for (String name : names) {
            commands[index - ARGUMENTS - 1] = index;
            nodes[index++] = new CommandNode(CommandType.LITERAL, true, false, new int[]{ARGUMENTS}, OptionalInt.empty(), name, null, null, null);
        }
        nodes[ROOT] = new CommandNode(CommandType.ROOT, false, false, commands, OptionalInt.empty(), null, null, null, null);
        nodes[ARGUMENTS] = new CommandNode(CommandType.ARGUMENT, true, false, new int[0], OptionalInt.empty(), "arguments", CommandParser.STRING, StringProperties.GREEDY_PHRASE, null);
        player.getJavaSession().send(new ClientboundCommandsPacket(nodes, ROOT));
    }
}
