package org.barrelmc.barrel.network.translator.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.Utils;
import org.barrelmc.barrel.utils.nukkit.BitArray;
import org.barrelmc.barrel.utils.nukkit.BitArrayVersion;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.util.stream.NetworkDataInputStream;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.level.HeightmapTypes;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.io.IOException;
import java.util.BitSet;
import java.util.Collections;

public class LevelChunkPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket) pk;

        // TODO: Request the sub chunks when the server does not send them along
        int subChunksLength = packet.isRequestSubChunks() ? 0 : packet.getSubChunksLength();
        ChunkSection[] chunkSections = Utils.createChunkSections();
        boolean hashedBlockIds = player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        int minSection = ProxyServer.getInstance().getOverworldMinSection();
        // Only the bedrock overworld goes below y 0
        int firstSection = packet.getDimension() == 0 ? 0 : -minSection;

        ByteBuf byteBuf = packet.getData();

        for (int subChunkIndex = 0; subChunkIndex < subChunksLength; subChunkIndex++) {
            int sectionIndex = firstSection + subChunkIndex;
            int chunkVersion = byteBuf.readByte();
            if (chunkVersion != 1 && chunkVersion != 8 && chunkVersion != 9) {
                // TODO: Support chunk version 0 (pm 3.0.0 legacy chunk)
                continue;
            }

            byte storageSize = 1;
            if (chunkVersion != 1) {
                storageSize = byteBuf.readByte();
            }
            if (chunkVersion == 9) {
                sectionIndex = byteBuf.readByte() - minSection; // height
            }

            // The java world is not as high as the bedrock one, read the sub chunk anyway to get to the next one
            ChunkSection chunkSection = sectionIndex >= 0 && sectionIndex < chunkSections.length ? chunkSections[sectionIndex] : Utils.createChunkSection();
            networkDecodeVersionEight(byteBuf, chunkSection, storageSize, hashedBlockIds);
            //TODO: Read biome
        }

        ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(
                packet.getChunkX(), packet.getChunkZ(),
                Utils.writeChunkSections(chunkSections), Collections.singletonMap(HeightmapTypes.MOTION_BLOCKING, new long[37]), new BlockEntityInfo[0],
                new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), Collections.emptyList(), Collections.emptyList())
        );

        player.getJavaSession().send(chunkPacket);
    }

    public void networkDecodeVersionEight(ByteBuf byteBuf, ChunkSection chunkSection, byte storageSize, boolean hashedBlockIds) {
        for (int storageReadIndex = 0; storageReadIndex < storageSize; storageReadIndex++) {
            if (storageReadIndex > 1) {
                return;
            }

            byte paletteHeader = byteBuf.readByte();
            boolean isRuntime = (paletteHeader & 1) == 1;
            int paletteVersion = (paletteHeader | 1) >> 1;

            BitArrayVersion bitArrayVersion = BitArrayVersion.get(paletteVersion, true);

            int maxBlocksInSection = 4096;
            BitArray bitArray = bitArrayVersion.createPalette(maxBlocksInSection);
            int wordsSize = bitArray.getWords().length;

            for (int wordIterationIndex = 0; wordIterationIndex < wordsSize; wordIterationIndex++) {
                int word = byteBuf.readIntLE();
                bitArray.getWords()[wordIterationIndex] = word;
            }

            // A sub chunk made of a single block has no words and no palette size
            int paletteSize = bitArrayVersion == BitArrayVersion.V0 ? 1 : VarInts.readInt(byteBuf);
            int[] sectionPalette = new int[paletteSize];
            NBTInputStream nbtStream = isRuntime ? null : new NBTInputStream(new NetworkDataInputStream(new ByteBufInputStream(byteBuf)));
            for (int i = 0; i < paletteSize; i++) {
                if (isRuntime) {
                    sectionPalette[i] = VarInts.readInt(byteBuf);
                } else {
                    try {
                        NbtMapBuilder map = ((NbtMap) nbtStream.readTag()).toBuilder();
                        map.replace("name", "minecraft:" + map.get("name").toString());
                        System.out.println(map.build().toString());
                        //sectionPalette[i] = BlockPaletteTranslator.getBedrockBlockId(BlockPaletteTranslator.bedrockStateFromNBTMap(map.build()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            int index = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        int paletteIndex = bitArray.get(index);
                        int mcbeBlockId = sectionPalette[paletteIndex];
                        int javaStateId = BlockConverter.bedrockRuntimeToJavaStateId(mcbeBlockId, hashedBlockIds);

                        if (storageReadIndex == 0) {
                            chunkSection.setBlock(x, y, z, javaStateId);
                        } else {
                            if (BlockConverter.isJavaWater(javaStateId)) {
                                int layer0 = chunkSection.getBlock(x, y, z);
                                if (layer0 != 0) {
                                    int waterlogged = BlockConverter.javaBlockToWaterlogged(layer0);
                                    if (waterlogged != 1) {
                                        chunkSection.setBlock(x, y, z, waterlogged);
                                    }
                                } else {
                                    chunkSection.setBlock(x, y, z, javaStateId);
                                }
                            }
                        }
                        index++;
                    }
                }
            }
        }
    }
}
