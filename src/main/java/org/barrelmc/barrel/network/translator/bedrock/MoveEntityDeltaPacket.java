package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket.Flag;

import java.util.Set;

public class MoveEntityDeltaPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket) pk;

        Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
        if (entity == null) {
            return;
        }

        // Only what changed is in the packet
        Set<Flag> flags = packet.getFlags();
        if (flags.contains(Flag.HAS_X)) {
            entity.setX(packet.getX());
        }
        if (flags.contains(Flag.HAS_Y)) {
            entity.setY(packet.getY() - (entity.isPlayer() ? Entity.PLAYER_EYE_HEIGHT : 0));
        }
        if (flags.contains(Flag.HAS_Z)) {
            entity.setZ(packet.getZ());
        }
        if (flags.contains(Flag.HAS_PITCH)) {
            entity.setPitch(packet.getPitch());
        }
        if (flags.contains(Flag.HAS_YAW)) {
            entity.setYaw(packet.getYaw());
        }
        if (flags.contains(Flag.HAS_HEAD_YAW)) {
            entity.setHeadYaw(packet.getHeadYaw());
        }
        TranslatorUtils.sendEntityPosition(player, packet.getRuntimeEntityId(), entity, flags.contains(Flag.ON_GROUND));
    }
}
