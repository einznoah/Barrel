package org.barrelmc.barrel.network.translator.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.BedrockBlocks;
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
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;

public class LevelChunkPacket implements BedrockPacketTranslator {

    // The light of a section, half a byte for each place, all of them the brightest
    private static final byte[] FULL_LIGHT = new byte[2048];
    // No dimension has more sub chunks than this
    private static final int MAX_SUB_CHUNKS = 32;
    // What biomes are told as when they are those of the sub chunk below
    private static final int SAME_AS_BELOW = 127;
    // The first byte of what a block holds, as of everything that is made of named parts
    private static final int COMPOUND_TAG = 10;

    static {
        Arrays.fill(FULL_LIGHT, (byte) 0xFF);
    }

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket) pk;

        if (packet.getDimension() != player.getDimension()) {
            // A chunk of the dimension the player has left
            return;
        }

        ChunkSection[] chunkSections = Utils.createChunkSections(player.getJavaDimension().sectionCount());
        BedrockBlocks.Column bedrockBlocks = player.getBedrockBlocks().startChunk(packet.getChunkX(), packet.getChunkZ());
        if (packet.isRequestSubChunks()) {
            // The server waits to be asked for the sub chunks, the chunk is sent when they are there
            player.getSubChunkRequests().request(packet.getChunkX(), packet.getChunkZ(), packet.getDimension(), packet.getSubChunkLimit(), chunkSections, bedrockBlocks);
            return;
        }

        boolean hashedBlockIds = player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        ByteBuf byteBuf = packet.getData();

        for (int subChunkIndex = 0; subChunkIndex < packet.getSubChunksLength(); subChunkIndex++) {
            readSubChunk(byteBuf, chunkSections, subChunkIndex, hashedBlockIds, bedrockBlocks);
            //TODO: Read biome
        }
        try {
            readBlockEntities(byteBuf, bedrockBlocks);
        } catch (RuntimeException | IOException e) {
            // The chunk is shown without what its blocks hold, a sign without its text for one
        }

        bedrockBlocks.joinDoors(chunkSections);
        sendChunk(player, packet.getChunkX(), packet.getChunkZ(), chunkSections, bedrockBlocks.getJavaBlockEntities(chunkSections.length));
    }

    // Behind the sub chunks of a chunk are its biomes, the blocks at the border of an education world, and then
    // what the blocks of the chunk hold
    private static void readBlockEntities(ByteBuf byteBuf, BedrockBlocks.Column bedrockBlocks) throws IOException {
        // The biomes of a sub chunk are told as its blocks are, by a first byte that is odd
        for (int biomes = 0; biomes < MAX_SUB_CHUNKS && byteBuf.isReadable() && (byteBuf.getByte(byteBuf.readerIndex()) & 1) != 0; biomes++) {
            int version = byteBuf.readUnsignedByte() >> 1;
            if (version == SAME_AS_BELOW) {
                continue;
            }
            BitArrayVersion bitArrayVersion = BitArrayVersion.get(version, true);
            byteBuf.skipBytes(bitArrayVersion.createPalette(4096).getWords().length * 4);
            for (int entries = bitArrayVersion == BitArrayVersion.V0 ? 1 : VarInts.readInt(byteBuf); entries > 0; entries--) {
                VarInts.readInt(byteBuf);
            }
        }
        if (byteBuf.isReadable()) {
            byteBuf.skipBytes(byteBuf.readUnsignedByte());
        }
        readBlockEntityData(byteBuf, bedrockBlocks);
    }

    // What blocks hold, one after the other to the end
    public static void readBlockEntityData(ByteBuf byteBuf, BedrockBlocks.Column bedrockBlocks) throws IOException {
        NBTInputStream nbtStream = new NBTInputStream(new NetworkDataInputStream(new ByteBufInputStream(byteBuf)));
        // A chunk that has none ends with a single byte that is not the start of one
        while (byteBuf.isReadable() && byteBuf.getByte(byteBuf.readerIndex()) == COMPOUND_TAG) {
            if (nbtStream.readTag() instanceof NbtMap blockEntity) {
                bedrockBlocks.setBlockEntityData(blockEntity);
            }
        }
    }

    // Reads a sub chunk into the section it is for. A sub chunk that tells its height itself goes to that one
    public static void readSubChunk(ByteBuf byteBuf, ChunkSection[] chunkSections, int sectionIndex, boolean hashedBlockIds, BedrockBlocks.Column bedrockBlocks) {
        int chunkVersion = byteBuf.readByte();
        if (chunkVersion != 1 && chunkVersion != 8 && chunkVersion != 9) {
            // TODO: Support chunk version 0 (pm 3.0.0 legacy chunk)
            return;
        }

        byte storageSize = 1;
        if (chunkVersion != 1) {
            storageSize = byteBuf.readByte();
        }
        if (chunkVersion == 9) {
            sectionIndex = byteBuf.readByte() - bedrockBlocks.getMinSection(); // height
        }

        // The java world is not as high as the bedrock one, read the sub chunk anyway to get to the next one
        ChunkSection chunkSection = sectionIndex >= 0 && sectionIndex < chunkSections.length ? chunkSections[sectionIndex] : Utils.createChunkSection();
        networkDecodeVersionEight(byteBuf, chunkSection, storageSize, hashedBlockIds, bedrockBlocks, sectionIndex);
    }

    public static void sendChunk(Player player, int chunkX, int chunkZ, ChunkSection[] chunkSections, BlockEntityInfo[] blockEntities) {
        // A bedrock server does not send light, its clients work it out themselves. A java client shows a chunk it
        // was sent no light for as dark, so every place is told to be in the light of the sky, also below the ground
        // TODO: Work out the light as a client does, with shadows and with what gives light
        int lightSections = chunkSections.length + 2;
        BitSet everywhere = new BitSet(lightSections);
        everywhere.set(0, lightSections);
        // Where the sky gives no light, in the nether and the end, the same is told of the light of blocks
        LightUpdateData light = player.getJavaDimension().skyLight()
                ? new LightUpdateData(everywhere, new BitSet(), new BitSet(), everywhere, Collections.nCopies(lightSections, FULL_LIGHT), Collections.emptyList())
                : new LightUpdateData(new BitSet(), everywhere, new BitSet(), new BitSet(), Collections.emptyList(), Collections.nCopies(lightSections, FULL_LIGHT));
        ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(
                chunkX, chunkZ,
                Utils.writeChunkSections(chunkSections), Collections.singletonMap(HeightmapTypes.MOTION_BLOCKING, new long[37]), blockEntities,
                light
        );

        player.getJavaSession().send(chunkPacket);
    }

    public static void networkDecodeVersionEight(ByteBuf byteBuf, ChunkSection chunkSection, byte storageSize, boolean hashedBlockIds, BedrockBlocks.Column bedrockBlocks, int sectionIndex) {
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

            if (storageReadIndex == 0 && isRuntime) {
                // The blocks as the server has them, for what the server is told of a block later
                bedrockBlocks.setSection(sectionIndex, bitArray, sectionPalette);
            }

            // What the blocks of the sub chunk are for a java client is looked up once for each kind of them
            int[] javaPalette = new int[paletteSize];
            boolean[] doors = new boolean[paletteSize];
            BlockEntityType[] blockEntities = new BlockEntityType[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                javaPalette[i] = BlockConverter.bedrockRuntimeToJavaStateId(sectionPalette[i], hashedBlockIds);
                doors[i] = BlockConverter.isJavaDoorLower(javaPalette[i]);
                blockEntities[i] = BlockConverter.getJavaBlockEntity(javaPalette[i]);
            }

            int index = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        int paletteIndex = bitArray.get(index);
                        int javaStateId = javaPalette[paletteIndex];

                        if (storageReadIndex == 0) {
                            chunkSection.setBlock(x, y, z, javaStateId);
                            if (doors[paletteIndex]) {
                                bedrockBlocks.addDoor(sectionIndex, x, y, z);
                            }
                            if (blockEntities[paletteIndex] != null) {
                                bedrockBlocks.addBlockEntity(sectionIndex, x, y, z, blockEntities[paletteIndex]);
                            }
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
