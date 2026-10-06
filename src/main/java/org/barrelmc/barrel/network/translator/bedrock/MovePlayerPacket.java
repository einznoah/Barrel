package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

public class MovePlayerPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket) pk;
        Vector3f position = packet.getPosition(), rotation = packet.getRotation();

        if (packet.getRuntimeEntityId() == player.getRuntimeEntityId()) {
            player.getJavaSession().send(new ClientboundPlayerPositionPacket(1, position.getX(), position.getY() - 1.62, position.getZ(), 0, 0, 0, rotation.getY(), rotation.getX()));
            player.setPosition(position.getX(), position.getY() - 1.62, position.getZ());
            if (packet.getMode() == org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode.TELEPORT || packet.getMode() == org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode.RESPAWN) {
                player.getInput().setTeleported();
            }
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
