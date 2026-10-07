package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.BlockEntityConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.BellValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.BellValueType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;

// What a block holds has changed, the text of a sign was written for one
public class BlockEntityDataPacket implements BedrockPacketTranslator {

    // What a java client is told with a bell that is struck, and a block of the java registry: the client looks at
    // the block that is at the place
    private static final int BELL_RINGS = 1;
    private static final int JAVA_BLOCK = 1;
    private static final Direction[] BELL_DIRECTIONS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket) pk;
        if (packet.getData() == null) {
            return;
        }

        player.getBedrockBlocks().setBlockEntityData(packet.getBlockPosition(), packet.getData());
        // A bell that starts to ring is told with what it holds, a java client is told that it was struck and from
        // where. The sides are numbered alike from the south on, a java client counts down and up before them
        if (packet.getData().getString("id", "").equals("Bell") && packet.getData().getBoolean("Ringing") && packet.getData().getInt("Ticks", 0) == 0) {
            Direction direction = BELL_DIRECTIONS[packet.getData().getInt("Direction", 0) & 3];
            player.getJavaSession().send(new ClientboundBlockEventPacket(packet.getBlockPosition(), BELL_RINGS, direction.ordinal(), BellValueType.SHAKE_DIRECTION, new BellValue(direction), JAVA_BLOCK));
        }
        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(player.getBedrockBlocks().getBlock(packet.getBlockPosition()), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        // The block can be another one for a java client with what it holds now, a bed of another color for one
        int shownBlock = BlockEntityConverter.getJavaBlock(javaBlock, packet.getData());
        // A chest is told whenever what it holds is: it can have stopped being one half of a large chest
        if (shownBlock != javaBlock || BlockConverter.isJavaChest(javaBlock)) {
            player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(packet.getBlockPosition(), shownBlock)));
        }
        BlockEntityType type = BlockConverter.getJavaBlockEntity(javaBlock);
        if (BlockEntityConverter.isTranslated(type)) {
            player.getJavaSession().send(new ClientboundBlockEntityDataPacket(packet.getBlockPosition(), type, BlockEntityConverter.bedrockToJava(type, packet.getData())));
        }
    }
}
