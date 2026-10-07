package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.Locale;
import java.util.UUID;

public class AddPaintingPacket implements BedrockPacketTranslator {

    // The directions as a bedrock server numbers them, and for each the one to its left
    private static final Direction[] DIRECTIONS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final Direction[] LEFT = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    // How far the middle of a painting is from the middle of the block it hangs in, towards the wall
    private static final double WALL_DISTANCE = 0.46875;
    // Where a java entity that hangs has which way it faces and a painting what it shows
    private static final int JAVA_DIRECTION = 8;
    private static final int JAVA_PAINTING_VARIANT = 9;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddPaintingPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddPaintingPacket) pk;

        // A bedrock server calls its paintings as a java one does, with capitals in place of the underscores
        String name = "minecraft:" + packet.getMotive().replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
        ProxyServer.Painting painting = ProxyServer.getInstance().getPainting(name);
        if (painting == null) {
            return;
        }

        // The server tells where the middle of the painting is. A java client is told the block it hangs in and
        // works the rest out itself: the middle is against the wall, and half a block to the left and up when the
        // painting is an even number of blocks wide or high
        int direction = packet.getDirection() & 3;
        Vector3f position = packet.getPosition();
        double left = painting.width() % 2 == 0 ? 0.5 : 0;
        double up = painting.height() % 2 == 0 ? 0.5 : 0;
        double x = Math.floor(position.getX() + getX(DIRECTIONS[direction]) * WALL_DISTANCE - getX(LEFT[direction]) * left);
        double y = Math.floor(position.getY() - up);
        double z = Math.floor(position.getZ() + getZ(DIRECTIONS[direction]) * WALL_DISTANCE - getZ(LEFT[direction]) * left);

        Entity entity = new Entity(EntityType.PAINTING);
        entity.setLocation(x, y, z, 0, 0);
        player.getEntities().put(packet.getRuntimeEntityId(), entity);
        player.getEntityRuntimeIds().put(packet.getUniqueEntityId(), packet.getRuntimeEntityId());

        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), UUID.randomUUID(), EntityType.PAINTING, DIRECTIONS[direction], x, y, z, 0, 0, 0));
        player.getJavaSession().send(new ClientboundSetEntityDataPacket((int) packet.getRuntimeEntityId(), new EntityMetadata<?, ?>[]{
                new ObjectEntityMetadata<>(JAVA_DIRECTION, MetadataTypes.DIRECTION, DIRECTIONS[direction]),
                new ObjectEntityMetadata<>(JAVA_PAINTING_VARIANT, MetadataTypes.PAINTING_VARIANT, Holder.ofId(painting.id()))
        }));
    }

    private static int getX(Direction direction) {
        return direction == Direction.EAST ? 1 : direction == Direction.WEST ? -1 : 0;
    }

    private static int getZ(Direction direction) {
        return direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? -1 : 0;
    }
}
