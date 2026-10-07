package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.ChestValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.ChestValueType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

// A block does something that is seen: the lid of a chest goes up or down
public class BlockEventPacket implements BedrockPacketTranslator {

    // What a server tells with a lid, and what a java client is told with one
    private static final int LID = 1;
    // A block of the java registry. A java client is told one with the event and then looks at the block that is at
    // the place, so any block it knows does
    private static final int JAVA_BLOCK = 1;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket) pk;
        if (packet.getEventType() != LID) {
            return;
        }

        // The same is told for a barrel, which is another block when it is open, and for a note block, whose note
        // a java client would take from a block that does not have it: of these it is the sound that is told
        Vector3i position = packet.getBlockPosition();
        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(player.getBedrockBlocks().getBlock(position), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        BlockEntityType type = BlockConverter.getJavaBlockEntity(javaBlock);
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST || type == BlockEntityType.ENDER_CHEST || type == BlockEntityType.SHULKER_BOX) {
            // A java client is told how many players look into it, the lid is up while that is anybody
            int viewers = packet.getEventData() != 0 ? 1 : 0;
            player.getJavaSession().send(new ClientboundBlockEventPacket(position, LID, viewers, ChestValueType.VIEWING_PLAYER_COUNT, new ChestValue(viewers), JAVA_BLOCK));
        }
    }
}
