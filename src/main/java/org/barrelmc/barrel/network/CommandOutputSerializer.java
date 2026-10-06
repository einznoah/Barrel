package org.barrelmc.barrel.network;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.CommandOutputSerializer_v898;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputType;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;

import java.util.Locale;

// The serializer of the library only takes the kind of an answer to a command when it is written in small letters,
// and not every server writes it so. What the kind is called is all that differs
public class CommandOutputSerializer extends CommandOutputSerializer_v898 {

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, CommandOutputPacket packet) {
        packet.setCommandOriginData(helper.readCommandOrigin(buffer));
        packet.setType(getType(helper.readString(buffer)));
        packet.setSuccessCount((int) buffer.readUnsignedIntLE());
        helper.readArray(buffer, packet.getMessages(), this::readMessage);
        packet.setData(helper.readOptional(buffer, (String) null, (optional, codecHelper) -> codecHelper.readString(optional)));
    }

    private static CommandOutputType getType(String name) {
        String wanted = name.toLowerCase(Locale.ROOT).replace("_", "");
        for (CommandOutputType type : CommandOutputType.values()) {
            if (type.name().toLowerCase(Locale.ROOT).replace("_", "").equals(wanted)) {
                return type;
            }
        }
        return CommandOutputType.NONE;
    }
}
