package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.UUID;

public class AddItemEntityPacket implements BedrockPacketTranslator {

    // Index of the item in the data of a java item entity
    private static final int JAVA_ITEM_METADATA = 8;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket) pk;

        if (ItemConverter.isEmpty(packet.getItemInHand())) {
            return;
        }

        Vector3f position = packet.getPosition();
        Vector3f motion = packet.getMotion();
        Entity entity = new Entity(EntityType.ITEM);
        entity.setPosition(position);
        player.getEntities().put(packet.getRuntimeEntityId(), entity);
        player.getEntityRuntimeIds().put(packet.getUniqueEntityId(), packet.getRuntimeEntityId());

        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), UUID.randomUUID(), EntityType.ITEM, position.getX(), position.getY(), position.getZ(), Vector3d.from(motion.getX(), motion.getY(), motion.getZ()), 0, 0, 0));
        player.getJavaSession().send(new ClientboundSetEntityDataPacket((int) packet.getRuntimeEntityId(), new EntityMetadata<?, ?>[]{new ObjectEntityMetadata<>(JAVA_ITEM_METADATA, MetadataTypes.ITEM_STACK, ItemConverter.bedrockToJavaItem(packet.getItemInHand()))}));
    }
}
