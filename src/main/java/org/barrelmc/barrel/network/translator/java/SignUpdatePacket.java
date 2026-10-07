package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.BlockEntityConverter;
import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;

// The player has written on a sign. A bedrock client sends the whole sign as it is then
public class SignUpdatePacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundSignUpdatePacket packet = (ServerboundSignUpdatePacket) pk;

        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(player.getBedrockBlocks().getBlock(packet.getPosition()), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        BlockEntityType type = BlockConverter.getJavaBlockEntity(javaBlock);
        if (!BlockEntityConverter.isTranslated(type)) {
            return;
        }

        NbtMap sign = BlockEntityConverter.getWrittenSign(type, packet.getPosition(), player.getBedrockBlocks().getBlockEntityData(packet.getPosition()), packet.getLines(), packet.isFrontText());
        player.getBedrockBlocks().setBlockEntityData(packet.getPosition(), sign);

        BlockEntityDataPacket blockEntityDataPacket = new BlockEntityDataPacket();
        blockEntityDataPacket.setBlockPosition(packet.getPosition());
        blockEntityDataPacket.setData(sign);
        player.getBedrockSession().sendPacket(blockEntityDataPacket);
    }
}
