package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// Many blocks that change at once, what an explosion leaves for one
public class UpdateSubChunkBlocksPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket) pk;

        // The extra blocks are the water a block is in, as with a single block they are left out
        for (org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry block : packet.getStandardBlocks()) {
            player.getBedrockBlocks().changeBlock(block.getPosition(), block.getDefinition().getRuntimeId());
        }
    }
}
