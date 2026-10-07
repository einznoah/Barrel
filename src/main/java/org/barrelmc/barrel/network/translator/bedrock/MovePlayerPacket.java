package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class MovePlayerPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket) pk;
        Vector3f position = packet.getPosition(), rotation = packet.getRotation();

        if (packet.getRuntimeEntityId() == player.getRuntimeEntityId()) {
            // The server puts the player somewhere. The java client is put there too, and until it says that it is
            // there the player is where the server put it
            player.setPosition(position.getX(), position.getY() - Entity.PLAYER_EYE_HEIGHT, position.getZ());
            player.setRotation(rotation.getY(), rotation.getX());
            if (packet.getMode() == org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode.TELEPORT || packet.getMode() == org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode.RESPAWN) {
                player.getInput().setTeleported();
            }
            player.getInput().teleportJava();
            player.setLastServerPosition(position);
            player.setLastServerRotation(rotation.toVector2());
        } else {
            Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
            if (entity != null) {
                entity.setLocation(position.getX(), position.getY() - Entity.PLAYER_EYE_HEIGHT, position.getZ(), rotation.getY(), rotation.getX());
                entity.setHeadYaw(rotation.getZ());
                TranslatorUtils.sendEntityPosition(player, packet.getRuntimeEntityId(), entity, packet.isOnGround());
            }
        }
    }
}
