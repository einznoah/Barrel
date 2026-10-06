package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;

// Many blocks that change at once, what an explosion leaves for one
public class UpdateSubChunkBlocksPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket) pk;

        boolean hashedBlockIds = player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        // The extra blocks are the water a block is in, as with a single block they are left out
        for (org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry block : packet.getStandardBlocks()) {
            int blockState = BlockConverter.bedrockRuntimeToJavaStateId(block.getDefinition().getRuntimeId(), hashedBlockIds);
            player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(block.getPosition(), blockState)));
        }
    }
}
