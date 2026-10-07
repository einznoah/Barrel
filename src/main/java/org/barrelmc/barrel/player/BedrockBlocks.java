package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.BlockEntityConverter;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.nukkit.BitArray;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.RecordEventData;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // The blocks of the chunk a java client draws through what they hold, by where they are in the same way
        private final Map<Integer, BlockEntityType> blockEntities = new HashMap<>();
        // What the server sent that its blocks hold, by their place in the chunk
        private final Map<Integer, NbtMap> blockEntityData = new HashMap<>();

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
            this.blockEntities.keySet().removeIf(blockEntity -> blockEntity >> 12 == section);
        }

        public void addBlockEntity(int section, int x, int y, int z, BlockEntityType type) {
            this.blockEntities.put(section << 12 | x << 8 | z << 4 | y, type);
        }

        // What a block holds as the server sent it, which tells itself where the block is
        public void setBlockEntityData(NbtMap data) {
            if (data.containsKey("x", NbtType.INT) && data.containsKey("y", NbtType.INT) && data.containsKey("z", NbtType.INT)
                    && data.getInt("x") >> 4 == this.x && data.getInt("z") >> 4 == this.z) {
                this.blockEntityData.put(this.getPlace(data.getInt("x"), data.getInt("y"), data.getInt("z")), data);
            }
        }

        // Some blocks are another java block for what they hold, a bed of its color for one. Done once all sub
        // chunks are there, what the blocks hold comes behind them
        public void applyBlockEntityData(ChunkSection[] sections) {
            for (Map.Entry<Integer, NbtMap> blockEntity : this.blockEntityData.entrySet()) {
                int section = blockEntity.getKey() >> 12, y = blockEntity.getKey() >> 8 & 15, x = blockEntity.getKey() >> 4 & 15, z = blockEntity.getKey() & 15;
                if (section < 0 || section >= sections.length) {
                    continue;
                }
                int javaBlock = sections[section].getBlock(x, y, z);
                int shownBlock = BlockEntityConverter.getJavaBlock(javaBlock, blockEntity.getValue());
                if (shownBlock != javaBlock) {
                    sections[section].setBlock(x, y, z, shownBlock);
                }
            }
        }

        // The java client is told with the chunk which of its blocks hold something, and what
        public BlockEntityInfo[] getJavaBlockEntities(int sectionCount) {
            List<BlockEntityInfo> javaBlockEntities = new ArrayList<>();
            for (Map.Entry<Integer, BlockEntityType> blockEntity : this.blockEntities.entrySet()) {
                int section = blockEntity.getKey() >> 12, x = blockEntity.getKey() >> 8 & 15, z = blockEntity.getKey() >> 4 & 15;
                if (section < 0 || section >= sectionCount) {
                    continue;
                }
                int y = (section + this.minSection << 4) + (blockEntity.getKey() & 15);
                NbtMap data = this.blockEntityData.get(this.getPlace(x, y, z));
                javaBlockEntities.add(new BlockEntityInfo(x, y, z, blockEntity.getValue(), BlockEntityConverter.bedrockToJava(blockEntity.getValue(), data)));
            }
            return javaBlockEntities.toArray(new BlockEntityInfo[0]);
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

    private static final int JAVA_AIR = 0;
    // The side a look enters a block by when it goes up or down an axis: x, y, z
    private static final Direction[][] ENTERED_BY = {{Direction.WEST, Direction.EAST}, {Direction.DOWN, Direction.UP}, {Direction.NORTH, Direction.SOUTH}};
    private static final Set<String> BEDROCK_LIQUIDS = Set.of("minecraft:water", "minecraft:flowing_water", "minecraft:lava", "minecraft:flowing_lava");

    // A liquid that is looked at: the block, the side the look enters it by and where on the block it does
    public record LiquidHit(Vector3i position, Direction face, Vector3f cursor) {
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
        // A jukebox that is taken away stops playing
        if (this.player.getJukeboxes().remove(position)) {
            this.player.getJavaSession().send(new ClientboundLevelEventPacket(LevelEventType.SOUND_STOP_JUKEBOX_SONG, position, new RecordEventData(0)));
        }

        int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(bedrockBlockId, hashed);
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column != null) {
            // A block that is gone holds nothing anymore. Another one is shown with what the server sent it holds:
            // a bed that someone lies down in is still of its color
            int place = column.getPlace(position.getX(), position.getY(), position.getZ());
            if (javaBlock == JAVA_AIR) {
                column.blockEntityData.remove(place);
            } else {
                int shownBlock = BlockEntityConverter.getJavaBlock(javaBlock, column.blockEntityData.get(place));
                if (shownBlock != javaBlock) {
                    this.player.getJavaSession().send(new ClientboundBlockUpdatePacket(new BlockChangeEntry(position, shownBlock)));
                    return;
                }
            }
        }
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

    // What the server sent that the block at a place holds, null when it sent nothing
    public NbtMap getBlockEntityData(Vector3i position) {
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        return column == null ? null : column.blockEntityData.get(column.getPlace(position.getX(), position.getY(), position.getZ()));
    }

    public void setBlockEntityData(Vector3i position, NbtMap data) {
        Column column = this.columns.get(getKey(position.getX() >> 4, position.getZ() >> 4));
        if (column != null) {
            column.blockEntityData.put(column.getPlace(position.getX(), position.getY(), position.getZ()), data);
        }
    }

    // The first liquid along a look, null when another block is in the way or there is none within reach. The
    // look goes from block to block, each time into the one whose side it gets to first
    public LiquidHit findLiquid(Vector3f from, Vector3f direction, float reach) {
        boolean hashed = this.player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        int air = BlockConverter.getBedrockAirId(hashed);
        int[] block = {(int) Math.floor(from.getX()), (int) Math.floor(from.getY()), (int) Math.floor(from.getZ())};
        float[] start = {from.getX(), from.getY(), from.getZ()}, way = {direction.getX(), direction.getY(), direction.getZ()};
        // How far along the look the next side of a block is on each axis, and how far it is from side to side
        double[] next = new double[3], step = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            step[axis] = way[axis] == 0 ? Double.MAX_VALUE : Math.abs(1 / way[axis]);
            next[axis] = way[axis] == 0 ? Double.MAX_VALUE : (way[axis] > 0 ? block[axis] + 1 - start[axis] : start[axis] - block[axis]) * step[axis];
        }

        // Eyes that are in the liquid look at it from above for what is done with it
        Direction face = Direction.UP;
        for (double far = 0; far <= reach; ) {
            Vector3i position = Vector3i.from(block[0], block[1], block[2]);
            int bedrockBlockId = this.getBlock(position);
            if (bedrockBlockId != air) {
                if (!BEDROCK_LIQUIDS.contains(String.valueOf(BlockConverter.getBedrockName(bedrockBlockId, hashed)))) {
                    return null;
                }
                Vector3f hit = from.add(direction.mul((float) far));
                return new LiquidHit(position, face, Vector3f.from(hit.getX() - block[0], hit.getY() - block[1], hit.getZ() - block[2]));
            }

            int axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
            far = next[axis];
            next[axis] += step[axis];
            block[axis] += way[axis] > 0 ? 1 : -1;
            face = ENTERED_BY[axis][way[axis] > 0 ? 0 : 1];
        }
        return null;
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
