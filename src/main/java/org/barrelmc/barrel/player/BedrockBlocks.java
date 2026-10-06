package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.nukkit.BitArray;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.HashMap;
import java.util.Map;

// The blocks the bedrock server has sent, by the ids the server has for them. A bedrock client tells the server
// which block it believes it clicks on, and a server of mojang does not take a click on a block it has another
// block at. The blocks are kept as they came, a list of the blocks of a sub chunk and a number into it for each
// place, with what changed since next to it. This belongs to the thread that translates the packets
public class BedrockBlocks {

    public static class Column {
        private final int x;
        private final int z;
        private final Map<Integer, BitArray> blocks = new HashMap<>();
        private final Map<Integer, int[]> palettes = new HashMap<>();
        // The blocks that changed since the sub chunk they are in came
        private final Map<Integer, Integer> changed = new HashMap<>();

        private Column(int x, int z) {
            this.x = x;
            this.z = z;
        }

        // The sections are counted as those of the java world, from its lowest one
        public void setSection(int section, BitArray blocks, int[] palette) {
            this.blocks.put(section, blocks);
            this.palettes.put(section, palette);
            this.changed.keySet().removeIf(place -> getSection((place >> 8) + getMinY()) == section);
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

    private static int getMinY() {
        return ProxyServer.getInstance().getOverworldMinSection() << 4;
    }

    private static int getSection(int y) {
        return (y >> 4) - ProxyServer.getInstance().getOverworldMinSection();
    }

    private static int getPlace(int x, int y, int z) {
        return y - getMinY() << 8 | (x & 15) << 4 | z & 15;
    }

    // A chunk comes, what was known of it is not true anymore
    public Column startChunk(int chunkX, int chunkZ) {
        // The chunks the player has left behind are not kept
        int reach = this.player.getRenderDistance() * 2 + 8;
        if (this.columns.size() > reach * reach) {
            this.columns.values().removeIf(column -> Math.abs(column.x - chunkX) > reach || Math.abs(column.z - chunkZ) > reach);
        }

        Column column = new Column(chunkX, chunkZ);
        this.columns.put(getKey(chunkX, chunkZ), column);
        return column;
    }

    public void clear() {
        this.columns.clear();
    }

    public void setBlock(Vector3i position, int bedrockBlockId) {
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column != null) {
            column.changed.put(getPlace(position.getX(), position.getY(), position.getZ()), bedrockBlockId);
        }
    }

    // What is not known is air: a sub chunk of nothing but air is not sent
    public int getBlock(Vector3i position) {
        int air = BlockConverter.getBedrockAirId(this.player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column == null) {
            return air;
        }

        Integer changed = column.changed.get(getPlace(position.getX(), position.getY(), position.getZ()));
        if (changed != null) {
            return changed;
        }
        int section = getSection(position.getY());
        BitArray blocks = column.blocks.get(section);
        if (blocks == null) {
            return air;
        }
        // The blocks of a sub chunk are in the order of x, then z, then y
        return column.palettes.get(section)[blocks.get((position.getX() & 15) << 8 | (position.getZ() & 15) << 4 | position.getY() & 15)];
    }
}
