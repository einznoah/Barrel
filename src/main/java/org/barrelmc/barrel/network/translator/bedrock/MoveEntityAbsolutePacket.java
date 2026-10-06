package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class MoveEntityAbsolutePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket packet = (org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket) pk;
        Vector3f position = packet.getPosition(), rotation = packet.getRotation();

        Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
        if (entity == null) {
            return;
        }

        entity.setLocation(position.getX(), position.getY() - (entity.isPlayer() ? Entity.PLAYER_EYE_HEIGHT : 0), position.getZ(), rotation.getY(), rotation.getX());
        entity.setHeadYaw(rotation.getZ());
        TranslatorUtils.sendEntityPosition(player, packet.getRuntimeEntityId(), entity, packet.isOnGround());
    }
}
