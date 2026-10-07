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

    private static final float BOAT_HEIGHT = 0.375F;
    private static final float BOAT_TURN = 90;
    // Index of the item in the data of a java entity that is a thrown item
    private static final int JAVA_ITEM_METADATA = 8;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket) pk;

        // TODO: Most of the entity data, a sheep keeps its wool
        // An entity a java client has no kind for is still there for the server: a seat an add-on brings, for one.
        // It is told as something that is not seen, so that what rides it has something to ride
        EntityType knownType = EntityConverter.bedrockToJavaEntityType(packet.getIdentifier());
        EntityType entityType = knownType == null ? EntityType.ITEM_DISPLAY : knownType;

        Vector3f position = packet.getPosition();
        Vector3f motion = packet.getMotion();
        Entity entity = new Entity(entityType);
        entity.setStandIn(knownType == null);
        if (entity.isBoat()) {
            // A bedrock server has a boat higher and a quarter further turned than a java client
            entity.setShownOffset(-BOAT_HEIGHT);
            entity.setShownYaw(-BOAT_TURN);
        }
        entity.setLocation(position.getX(), position.getY(), position.getZ(), packet.getRotation().getY(), packet.getRotation().getX());
        entity.setHeadYaw(packet.getHeadRotation());
        player.getEntities().put(packet.getRuntimeEntityId(), entity);
        player.getEntityRuntimeIds().put(packet.getUniqueEntityId(), packet.getRuntimeEntityId());

        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), UUID.randomUUID(), entityType, position.getX(), position.getY() + entity.getShownOffset(), position.getZ(), Vector3d.from(motion.getX(), motion.getY(), motion.getZ()), entity.yaw + entity.getShownYaw(), entity.pitch, entity.getHeadYaw()));

        TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), entity, packet.getMetadata());
        if (entityType == EntityType.SPLASH_POTION || entityType == EntityType.LINGERING_POTION) {
            // A thrown potion is shown as the item it was, the bedrock server only tells which potion it holds
            Short bedrockPotionId = packet.getMetadata().get(EntityDataTypes.AUX_VALUE_DATA);
            ItemStack javaItem = ItemConverter.getJavaPotion(entityType == EntityType.SPLASH_POTION ? "minecraft:splash_potion" : "minecraft:lingering_potion", bedrockPotionId == null ? 0 : bedrockPotionId);
            player.getJavaSession().send(new ClientboundSetEntityDataPacket((int) packet.getRuntimeEntityId(), new EntityMetadata<?, ?>[]{new ObjectEntityMetadata<>(JAVA_ITEM_METADATA, MetadataTypes.ITEM_STACK, javaItem)}));
        }
        packet.getEntityLinks().forEach(player.getRiding()::link);
    }
}
