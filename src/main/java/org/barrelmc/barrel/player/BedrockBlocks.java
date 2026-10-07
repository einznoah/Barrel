package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.nukkit.BitArray;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The blocks the bedrock server has sent, by the ids the server has for them. A bedrock client tells the server
// which block it believes it clicks on, and a server of mojang does not take a click on a block it has another
// block at. The blocks are kept as they came, a list of the blocks of a sub chunk and a number into it for each
// place, with what changed since next to it. This belongs to the thread that translates the packets
public class BedrockBlocks {

    public static class Column {
        private final int x;
        private final int z;
        // The lowest section of the dimension the chunk is in
        private final int minSection;
        private final Map<Integer, BitArray> blocks = new HashMap<>();
        private final Map<Integer, int[]> palettes = new HashMap<>();
        // The blocks that changed since the sub chunk they are in came
        private final Map<Integer, Integer> changed = new HashMap<>();
        // Where the lower halves of the doors are that came with the sub chunks: the section, then x, z and y in it
        private final List<Integer> doors = new ArrayList<>();

        private Column(int x, int z, int minSection) {
            this.x = x;
            this.z = z;
            this.minSection = minSection;
        }

        public int getMinSection() {
            return this.minSection;
        }

        // The number of a place in the chunk: how high it is above the lowest block, then x and z
        private int getPlace(int x, int y, int z) {
            return y - (this.minSection << 4) << 8 | (x & 15) << 4 | z & 15;
        }

        // The sections are counted as those of the java world, from its lowest one
        public void setSection(int section, BitArray blocks, int[] palette) {
            this.blocks.put(section, blocks);
            this.palettes.put(section, palette);
            this.changed.keySet().removeIf(place -> place >> 12 == section);
            this.doors.removeIf(door -> door >> 12 == section);
        }

        public void addDoor(int section, int x, int y, int z) {
            this.doors.add(section << 12 | x << 8 | z << 4 | y);
        }

        // The halves of the doors are put together as a java client shows them, once all sub chunks are there: the
        // upper half of a door can be in the next one
        public void joinDoors(ChunkSection[] sections) {
            for (int door : this.doors) {
                int section = door >> 12, x = door >> 8 & 15, z = door >> 4 & 15, y = door & 15;
                int upperSection = y == 15 ? section + 1 : section, upperY = y + 1 & 15;
                if (section < 0 || upperSection >= sections.length) {
                    continue;
                }

                int lowerHalf = sections[section].getBlock(x, y, z);
                int upperHalf = sections[upperSection].getBlock(x, upperY, z);
                if (BlockConverter.isJavaDoor(lowerHalf, upperHalf)) {
                    sections[section].setBlock(x, y, z, BlockConverter.getJavaDoorLower(lowerHalf, upperHalf));
                    sections[upperSection].setBlock(x, upperY, z, BlockConverter.getJavaDoorUpper(lowerHalf, upperHalf));
                }
            }
        }
    }

    private final Player player;
    private final Map<Long, Column> columns = new HashMap<>();

    public BedrockBlocks(Player player) {
        this.player = player;
    }

    private static long getKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    // A chunk comes, what was known of it is not true anymore
    public Column startChunk(int chunkX, int chunkZ) {
        // The chunks the player has left behind are not kept
        int reach = this.player.getRenderDistance() * 2 + 8;
        if (this.columns.size() > reach * reach) {
            this.columns.values().removeIf(column -> Math.abs(column.x - chunkX) > reach || Math.abs(column.z - chunkZ) > reach);
        }

        Column column = new Column(chunkX, chunkZ, this.player.getJavaDimension().minSection());
        this.columns.put(getKey(chunkX, chunkZ), column);
        return column;
    }

    public void clear() {
        this.columns.clear();
    }

    public void setBlock(Vector3i position, int bedrockBlockId) {
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column != null) {
            column.changed.put(column.getPlace(position.getX(), position.getY(), position.getZ()), bedrockBlockId);
        }
    }

    // The server has told that the block at a place is another one now, and the java client is told. The other
    // half of a door is told as well, what it shows is partly kept with this half
    public void changeBlock(Vector3i position, int bedrockBlockId) {
        boolean hashed = this.player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        this.setBlock(position, bedrockBlockId);

        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(bedrockBlockId, hashed);
        if (BlockConverter.isJavaDoorHalf(javaBlock)) {
            boolean isLower = BlockConverter.isJavaDoorLower(javaBlock);
            Vector3i lower = isLower ? position : position.down();
            Vector3i upper = lower.up();
            int lowerHalf = isLower ? javaBlock : BlockConverter.bedrockRuntimeToJavaStateId(this.getBlock(lower), hashed);
            int upperHalf = isLower ? BlockConverter.bedrockRuntimeToJavaStateId(this.getBlock(upper), hashed) : javaBlock;
            if (BlockConverter.isJavaDoor(lowerHalf, upperHalf)) {
                this.player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(lower, BlockConverter.getJavaDoorLower(lowerHalf, upperHalf))));
                this.player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(upper, BlockConverter.getJavaDoorUpper(lowerHalf, upperHalf))));
                return;
            }
        }
        this.player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(position, javaBlock)));
    }

    // What is not known is air: a sub chunk of nothing but air is not sent
    public int getBlock(Vector3i position) {
        int air = BlockConverter.getBedrockAirId(this.player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column == null) {
            return air;
        }

        Integer changed = column.changed.get(column.getPlace(position.getX(), position.getY(), position.getZ()));
        if (changed != null) {
            return changed;
        }
        int section = (position.getY() >> 4) - column.minSection;
        BitArray blocks = column.blocks.get(section);
        if (blocks == null) {
            return air;
        }
        // The blocks of a sub chunk are in the order of x, then z, then y
        return column.palettes.get(section)[blocks.get((position.getX() & 15) << 8 | (position.getZ() & 15) << 4 | position.getY() & 15)];
    }
}
