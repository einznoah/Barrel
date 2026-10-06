package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.EntityConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;

import java.util.UUID;

public class AddEntityPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket) pk;

        // TODO: The entity data, a sheep keeps its wool and every animal is an adult
        EntityType entityType = EntityConverter.bedrockToJavaEntityType(packet.getIdentifier());
        if (entityType == null) {
            return;
        }

        Vector3f position = packet.getPosition();
        Vector3f motion = packet.getMotion();
        Entity entity = new Entity(false);
        entity.setLocation(position.getX(), position.getY(), position.getZ(), packet.getRotation().getY(), packet.getRotation().getX());
        entity.setHeadYaw(packet.getHeadRotation());
        player.getEntities().put(packet.getRuntimeEntityId(), entity);

        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), UUID.randomUUID(), entityType, position.getX(), position.getY(), position.getZ(), Vector3d.from(motion.getX(), motion.getY(), motion.getZ()), entity.yaw, entity.pitch, entity.getHeadYaw()));
    }
}
