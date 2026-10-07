package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.BlockEntityConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;

// What a block holds has changed, the text of a sign was written for one
public class BlockEntityDataPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket) pk;
        if (packet.getData() == null) {
            return;
        }

        player.getBedrockBlocks().setBlockEntityData(packet.getBlockPosition(), packet.getData());
        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(player.getBedrockBlocks().getBlock(packet.getBlockPosition()), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        // The block can be another one for a java client with what it holds now, a bed of another color for one
        int shownBlock = BlockEntityConverter.getJavaBlock(javaBlock, packet.getData());
        if (shownBlock != javaBlock) {
            player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(packet.getBlockPosition(), shownBlock)));
        }
        BlockEntityType type = BlockConverter.getJavaBlockEntity(javaBlock);
        if (BlockEntityConverter.isTranslated(type)) {
            player.getJavaSession().send(new ClientboundBlockEntityDataPacket(packet.getBlockPosition(), type, BlockEntityConverter.bedrockToJava(type, packet.getData())));
        }
    }
}
