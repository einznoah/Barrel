package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.EntityConverter;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.UUID;

public class AddEntityPacket implements BedrockPacketTranslator {

    // Index of the item in the data of a java entity that is a thrown item
    private static final int JAVA_ITEM_METADATA = 8;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket) pk;

        // TODO: Most of the entity data, a sheep keeps its wool
        EntityType entityType = EntityConverter.bedrockToJavaEntityType(packet.getIdentifier());
        if (entityType == null) {
            return;
        }

        Vector3f position = packet.getPosition();
        Vector3f motion = packet.getMotion();
        Entity entity = new Entity(entityType);
        entity.setLocation(position.getX(), position.getY(), position.getZ(), packet.getRotation().getY(), packet.getRotation().getX());
        entity.setHeadYaw(packet.getHeadRotation());
        player.getEntities().put(packet.getRuntimeEntityId(), entity);
        player.getEntityRuntimeIds().put(packet.getUniqueEntityId(), packet.getRuntimeEntityId());

        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), UUID.randomUUID(), entityType, position.getX(), position.getY(), position.getZ(), Vector3d.from(motion.getX(), motion.getY(), motion.getZ()), entity.yaw, entity.pitch, entity.getHeadYaw()));

        TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), entity, packet.getMetadata());
        if (entityType == EntityType.SPLASH_POTION || entityType == EntityType.LINGERING_POTION) {
            // A thrown potion is shown as the item it was, the bedrock server only tells which potion it holds
            Short bedrockPotionId = packet.getMetadata().get(EntityDataTypes.AUX_VALUE_DATA);
            ItemStack javaItem = ItemConverter.getJavaPotion(entityType == EntityType.SPLASH_POTION ? "minecraft:splash_potion" : "minecraft:lingering_potion", bedrockPotionId == null ? 0 : bedrockPotionId);
            player.getJavaSession().send(new ClientboundSetEntityDataPacket((int) packet.getRuntimeEntityId(), new EntityMetadata<?, ?>[]{new ObjectEntityMetadata<>(JAVA_ITEM_METADATA, MetadataTypes.ITEM_STACK, javaItem)}));
        }
    }
}
