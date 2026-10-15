package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.LightConverter;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// A bedrock server sends no light, its clients work it out themselves. A java client is sent the light with each
// chunk, and works out itself only how a block it is told of changes the light. So the light is worked out here as
// a java server does: the light of the sky comes straight down to the first block that stops it and spreads from
// there, and blocks that give light spread theirs. Light gets one less from block to block, or as much less as a
// block takes away, and the sides of blocks like slabs and stairs stop it. A chunk that comes spreads light into
// the chunks next to it, which are told what changed in them once a tick, as are the changes of blocks.
// This belongs to the thread that translates the packets
public class Light {

    private static final int SKY = 0;
    private static final int BLOCK = 1;
    private static final int MAX_LIGHT = 15;
    private static final int SECTION_BLOCKS = 4096;
    // The light of a section is half a byte for each block
    private static final int LIGHT_BYTES = 2048;
    // The sides of a block in the order of LightConverter: down, up, north, south, west, east
    private static final int[] SIDE_X = {0, 0, 0, 0, -1, 1};
    private static final int[] SIDE_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] SIDE_Z = {0, 0, -1, 1, 0, 0};
    // The sides that lead to the chunks next to one: north, south, west, east
    private static final int FIRST_LEVEL_SIDE = 2;
    // A section of light in which every block has the same light, for each light there is
    private static final byte[][] FILLED = new byte[MAX_LIGHT + 1][LIGHT_BYTES];
    // The palette of a section is looked at as a whole up to this size, a larger one block by block
    private static final int MAX_PALETTE = 256;
    // Where a block is goes into one number: x and z made positive, and the height above the lowest block
    private static final int COORDINATE_OFFSET = 1 << 25;
    private static final int COORDINATE_MASK = (1 << 26) - 1;
    private static final int HEIGHT_MASK = (1 << 12) - 1;
    // How many chunks beyond its view distance a java client keeps
    private static final int KEPT_BEYOND_VIEW = 3;

    static {
        for (int level = 0; level <= MAX_LIGHT; level++) {
            Arrays.fill(FILLED[level], (byte) (level << 4 | level));
        }
    }

    // The kinds of light of the blocks of a section: a list of the kinds there are in it and half a byte for each
    // block that tells which one it is. A section with more than 16 kinds has a byte for each block that is the kind
    private static final class Kinds {
        private int[] palette = new int[4];
        private int size = 1;
        // Nothing while all blocks are of the first kind of the list
        private byte[] blocks;
        private boolean wide;

        private Kinds(int kind) {
            this.palette[0] = kind;
        }

        private boolean isUniform() {
            return this.blocks == null;
        }

        private int get(int index) {
            if (this.blocks == null) {
                return this.palette[0];
            }
            if (this.wide) {
                return this.blocks[index] & 0xFF;
            }
            return this.palette[this.blocks[index >> 1] >> ((index & 1) << 2) & 15];
        }

        // The place of a kind in the list, which it is added to when it is not in it yet
        private int add(int kind) {
            int entry = 0;
            while (entry < this.size && this.palette[entry] != kind) {
                entry++;
            }
            if (entry == this.size) {
                if (this.size == this.palette.length) {
                    this.palette = Arrays.copyOf(this.palette, this.size * 2);
                }
                this.palette[this.size++] = kind;
            }
            return entry;
        }

        private void set(int index, int kind) {
            if (this.blocks == null) {
                if (kind == this.palette[0]) {
                    return;
                }
                this.blocks = new byte[LIGHT_BYTES];
            }
            if (!this.wide && this.size == 16 && this.add(kind) == 16) {
                // More kinds than half a byte tells apart, each block gets a byte with its kind
                this.size--;
                byte[] wideBlocks = new byte[SECTION_BLOCKS];
                for (int i = 0; i < SECTION_BLOCKS; i++) {
                    wideBlocks[i] = (byte) this.get(i);
                }
                this.blocks = wideBlocks;
                this.wide = true;
            }
            if (this.wide) {
                this.blocks[index] = (byte) kind;
                return;
            }
            int entry = this.add(kind), shift = (index & 1) << 2;
            this.blocks[index >> 1] = (byte) (this.blocks[index >> 1] & ~(15 << shift) | entry << shift);
        }

        // Whether a block of the section can give light
        private boolean mayGiveLight() {
            if (this.wide) {
                return true;
            }
            for (int entry = 0; entry < this.size; entry++) {
                if (LightConverter.getEmission(this.palette[entry]) > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    // A chunk with the light of its blocks. Its sections of light are counted as a java client counts them: the
    // first is the one below the world, the last the one above it
    private static final class Column {
        private final int x;
        private final int z;
        private Kinds[] kinds;
        private final byte[][][] light;
        // The light of all blocks of a section of light that has no bytes of its own
        private final int[][] fill;
        // The sections of light that changed since the java client was told, a bit for each
        private final long[] changed = new long[2];
        // The lowest height to which the light of the sky comes straight down, for each place: z, then x
        private final int[] lowestSky = new int[256];

        private Column(int x, int z, Kinds[] kinds) {
            this.x = x;
            this.z = z;
            this.kinds = kinds;
            int lightSections = kinds.length + 2;
            this.light = new byte[2][lightSections][];
            this.fill = new int[2][lightSections];
            // Above the world the sky gives all its light
            this.fill[SKY][lightSections - 1] = MAX_LIGHT;
        }

        private int getKind(int x, int y, int z) {
            return this.kinds[y >> 4].get((y & 15) << 8 | z << 4 | x);
        }

        private int getLight(int layer, int x, int y, int z) {
            byte[] data = this.light[layer][(y >> 4) + 1];
            if (data == null) {
                return this.fill[layer][(y >> 4) + 1];
            }
            int index = (y & 15) << 8 | z << 4 | x;
            return data[index >> 1] >> ((index & 1) << 2) & 15;
        }
    }

    // Places of blocks waiting their turn, with a light for each where that is needed
    private static final class Queue {
        private long[] places = new long[1024];
        private byte[] levels = new byte[1024];
        private int head;
        private int tail;
        private int lastLevel;

        private boolean isEmpty() {
            return this.head == this.tail;
        }

        private void add(long place, int level) {
            if (this.tail == this.places.length) {
                if (this.head > this.places.length / 2) {
                    // Most of it is done with, what is left moves to the front
                    System.arraycopy(this.places, this.head, this.places, 0, this.tail - this.head);
                    System.arraycopy(this.levels, this.head, this.levels, 0, this.tail - this.head);
                } else {
                    this.places = Arrays.copyOf(this.places, this.places.length * 2);
                    this.levels = Arrays.copyOf(this.levels, this.levels.length * 2);
                    System.arraycopy(this.places, this.head, this.places, 0, this.tail - this.head);
                    System.arraycopy(this.levels, this.head, this.levels, 0, this.tail - this.head);
                }
                this.tail -= this.head;
                this.head = 0;
            }
            this.places[this.tail] = place;
            this.levels[this.tail++] = (byte) level;
        }

        private long poll() {
            this.lastLevel = this.levels[this.head];
            long place = this.places[this.head++];
            if (this.head == this.tail) {
                this.head = this.tail = 0;
            }
            return place;
        }

        private void clear() {
            this.head = this.tail = 0;
        }
    }

    private final Player player;
    private final Map<Long, Column> columns = new HashMap<>();
    // The chunks whose light changed since the java client was told
    private final Set<Column> changedColumns = new LinkedHashSet<>();
    // Places whose light may spread, and places whose light went, with the light they had
    private final Queue brighter = new Queue();
    private final Queue darker = new Queue();
    private int minSection;
    private int sectionCount;
    private int height;
    private boolean skyLight;
    private int airKind;
    // The chunk looked at last, most blocks that are looked at are in the same chunk as the one before
    private Column lastColumn;
    private boolean started;

    public Light(Player player) {
        this.player = player;
    }

    private static long getKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    private static long getPlace(int x, int y, int z) {
        return (long) (x + COORDINATE_OFFSET & COORDINATE_MASK) << 38 | (long) (z + COORDINATE_OFFSET & COORDINATE_MASK) << 12 | y & HEIGHT_MASK;
    }

    private static int getX(long place) {
        return (int) (place >>> 38) - COORDINATE_OFFSET;
    }

    private static int getZ(long place) {
        return (int) (place >>> 12 & COORDINATE_MASK) - COORDINATE_OFFSET;
    }

    private static int getY(long place) {
        return (int) (place & HEIGHT_MASK);
    }

    private Column getColumn(int chunkX, int chunkZ) {
        Column column = this.lastColumn;
        if (column != null && column.x == chunkX && column.z == chunkZ) {
            return column;
        }
        column = this.columns.get(getKey(chunkX, chunkZ));
        if (column != null) {
            this.lastColumn = column;
        }
        return column;
    }

    private void setLight(Column column, int layer, int x, int y, int z, int level) {
        int section = (y >> 4) + 1;
        byte[] data = column.light[layer][section];
        if (data == null) {
            if (level == column.fill[layer][section]) {
                return;
            }
            data = column.light[layer][section] = FILLED[column.fill[layer][section]].clone();
        }
        int index = (y & 15) << 8 | z << 4 | x, shift = (index & 1) << 2;
        data[index >> 1] = (byte) (data[index >> 1] & ~(15 << shift) | level << shift);
        if (column.changed[SKY] == 0 && column.changed[BLOCK] == 0) {
            this.changedColumns.add(column);
        }
        column.changed[layer] |= 1L << section;
    }

    public void clear() {
        this.columns.clear();
        this.changedColumns.clear();
        this.brighter.clear();
        this.darker.clear();
        this.lastColumn = null;
    }

    // A chunk comes or comes again, with its blocks as the java client is sent them. Its light is worked out, with
    // what comes in from the chunks next to it and goes out to them, and is what the java client is sent with it
    public LightUpdateData addChunk(int chunkX, int chunkZ, ChunkSection[] sections) {
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tellChanges);
        }
        ProxyServer.Dimension dimension = this.player.getJavaDimension();
        if (dimension.minSection() != this.minSection || sections.length != this.sectionCount || dimension.skyLight() != this.skyLight) {
            this.clear();
            this.minSection = dimension.minSection();
            this.sectionCount = sections.length;
            this.height = sections.length << 4;
            this.skyLight = dimension.skyLight();
            this.airKind = LightConverter.getKind(0);
        }
        this.forgetFarColumns();

        Kinds[] kinds = new Kinds[sections.length];
        for (int section = 0; section < sections.length; section++) {
            kinds[section] = this.getKinds(sections[section]);
        }
        long key = getKey(chunkX, chunkZ);
        Column column = this.columns.get(key);
        if (column == null) {
            column = new Column(chunkX, chunkZ, kinds);
            this.columns.put(key, column);
            this.lightNewColumn(column);
        } else {
            this.replaceBlocks(column, kinds);
        }

        // The java client gets all of its light with the chunk
        column.changed[SKY] = column.changed[BLOCK] = 0;
        this.changedColumns.remove(column);
        long all = (1L << sections.length + 2) - 1;
        return this.getLightData(column, all, all);
    }

    // The server has told that a block is another one now, as the java client is told
    public void changeBlock(Vector3i position, int javaStateId) {
        Column column = this.getColumn(position.getX() >> 4, position.getZ() >> 4);
        int x = position.getX() & 15, y = position.getY() - (this.minSection << 4), z = position.getZ() & 15;
        if (column == null || y < 0 || y >= this.height) {
            return;
        }
        int kind = LightConverter.getKind(javaStateId);
        if (column.getKind(x, y, z) == kind) {
            return;
        }
        column.kinds[y >> 4].set((y & 15) << 8 | z << 4 | x, kind);
        this.relight(column, new int[]{y << 8 | z << 4 | x}, 1);
    }

    private Kinds getKinds(ChunkSection section) {
        if (section.getBlockCount() == 0) {
            return new Kinds(this.airKind);
        }
        DataPalette data = section.getBlockData();
        Palette palette = data.getPalette();
        BitStorage storage = data.getStorage();
        if (storage == null || palette.size() == 1) {
            return new Kinds(LightConverter.getKind(palette.idToState(0)));
        }
        // The blocks of a section are kept in the same order as their light: y, then z, then x
        Kinds kinds = new Kinds(LightConverter.getKind(palette.idToState(storage.get(0))));
        if (palette.size() > MAX_PALETTE) {
            for (int index = 0; index < SECTION_BLOCKS; index++) {
                kinds.set(index, LightConverter.getKind(palette.idToState(storage.get(index))));
            }
            return kinds;
        }
        // Mostly the blocks of a section are of few kinds of light, stone and ores of the same one. Each block of the
        // palette is looked up once
        int[] entries = new int[palette.size()];
        for (int id = 0; id < entries.length; id++) {
            entries[id] = kinds.add(LightConverter.getKind(palette.idToState(id)));
        }
        if (kinds.size == 1) {
            return kinds;
        }
        if (kinds.size > 16) {
            kinds.blocks = new byte[SECTION_BLOCKS];
            kinds.wide = true;
            for (int index = 0; index < SECTION_BLOCKS; index++) {
                kinds.blocks[index] = (byte) kinds.palette[entries[storage.get(index)]];
            }
            return kinds;
        }
        kinds.blocks = new byte[LIGHT_BYTES];
        for (int index = 0; index < SECTION_BLOCKS; index += 2) {
            kinds.blocks[index >> 1] = (byte) (entries[storage.get(index)] | entries[storage.get(index + 1)] << 4);
        }
        return kinds;
    }

    // The chunks the java client has dropped are not kept
    private void forgetFarColumns() {
        int reach = this.player.getRenderDistance() + KEPT_BEYOND_VIEW;
        if (this.columns.size() <= (2 * reach + 1) * (2 * reach + 1)) {
            return;
        }
        int centerX = this.player.getFloorX() >> 4, centerZ = this.player.getFloorZ() >> 4;
        this.columns.values().removeIf(column -> Math.abs(column.x - centerX) > reach || Math.abs(column.z - centerZ) > reach);
        this.changedColumns.removeIf(column -> this.columns.get(getKey(column.x, column.z)) != column);
        this.lastColumn = null;
    }

    private void lightNewColumn(Column column) {
        int baseX = column.x << 4, baseZ = column.z << 4;
        for (int section = 0; section < this.sectionCount; section++) {
            Kinds kinds = column.kinds[section];
            if (!kinds.mayGiveLight()) {
                continue;
            }
            for (int index = 0; index < SECTION_BLOCKS; index++) {
                int emission = LightConverter.getEmission(kinds.get(index));
                if (emission > 0) {
                    int x = index & 15, y = section << 4 | index >> 8, z = index >> 4 & 15;
                    this.setLight(column, BLOCK, x, y, z, emission);
                    this.brighter.add(getPlace(baseX + x, y, baseZ + z), 0);
                }
            }
        }
        this.pullFromNeighbours(column, BLOCK);
        this.spreadLight(BLOCK);

        if (this.skyLight) {
            this.lightSky(column);
            this.pullFromNeighbours(column, SKY);
            this.spreadLight(SKY);
        }
    }

    // The lowest height to which the light of the sky comes straight down at a place of a chunk, the height of the
    // world when none does
    private int findLowestSky(Column column, int x, int z) {
        int above = this.airKind;
        for (int section = this.sectionCount - 1; section >= 0; section--) {
            Kinds kinds = column.kinds[section];
            if (kinds.isUniform()) {
                int kind = kinds.get(0);
                if (!LightConverter.stopsSkyLight(above, kind) && !LightConverter.stopsSkyLight(kind, kind)) {
                    above = kind;
                    continue;
                }
            }
            for (int y = 15; y >= 0; y--) {
                int kind = kinds.get(y << 8 | z << 4 | x);
                if (LightConverter.stopsSkyLight(above, kind)) {
                    return (section << 4) + y + 1;
                }
                above = kind;
            }
        }
        return 0;
    }

    // The light of the sky comes down into a new chunk, and from where it stops it spreads
    private void lightSky(Column column) {
        int lowest = this.height, highest = 0;
        for (int place = 0; place < 256; place++) {
            int sky = this.findLowestSky(column, place & 15, place >> 4);
            column.lowestSky[place] = sky;
            lowest = Math.min(lowest, sky);
            highest = Math.max(highest, sky);
        }
        for (int section = 0; section < this.sectionCount; section++) {
            int bottom = section << 4;
            if (bottom >= highest) {
                column.fill[SKY][section + 1] = MAX_LIGHT;
            } else if (bottom + 16 > lowest) {
                for (int place = 0; place < 256; place++) {
                    for (int y = Math.max(bottom, column.lowestSky[place]); y < bottom + 16; y++) {
                        this.setLight(column, SKY, place & 15, y, place >> 4, MAX_LIGHT);
                    }
                }
            }
        }

        // The light spreads from the lowest place it comes down to, and sideways from where it comes down further
        // than next to it
        int baseX = column.x << 4, baseZ = column.z << 4;
        for (int place = 0; place < 256; place++) {
            int x = place & 15, z = place >> 4, sky = column.lowestSky[place];
            if (sky >= this.height) {
                continue;
            }
            int top = sky + 1;
            for (int side = FIRST_LEVEL_SIDE; side < LightConverter.SIDES; side++) {
                int nextX = baseX + x + SIDE_X[side], nextZ = baseZ + z + SIDE_Z[side];
                Column next = this.getColumn(nextX >> 4, nextZ >> 4);
                if (next != null) {
                    top = Math.max(top, next.lowestSky[(nextZ & 15) << 4 | nextX & 15]);
                }
            }
            for (int y = sky; y < Math.min(top, this.height); y++) {
                this.brighter.add(getPlace(baseX + x, y, baseZ + z), 0);
            }
        }
    }

    // The light of the chunks next to a chunk that came spreads into it
    private void pullFromNeighbours(Column column, int layer) {
        for (int side = FIRST_LEVEL_SIDE; side < LightConverter.SIDES; side++) {
            Column next = this.getColumn(column.x + SIDE_X[side], column.z + SIDE_Z[side]);
            if (next == null) {
                continue;
            }
            for (int along = 0; along < 16; along++) {
                // The place in the chunk next to it and the place of this chunk it touches
                int x = SIDE_X[side] == 0 ? along : SIDE_X[side] < 0 ? 15 : 0;
                int z = SIDE_Z[side] == 0 ? along : SIDE_Z[side] < 0 ? 15 : 0;
                int ownX = SIDE_X[side] == 0 ? along : 15 - x, ownZ = SIDE_Z[side] == 0 ? along : 15 - z;
                for (int y = 0; y < this.height; y++) {
                    int level = next.getLight(layer, x, y, z);
                    if (level > 1 && column.getLight(layer, ownX, y, ownZ) < level - 1) {
                        this.brighter.add(getPlace(next.x << 4 | x, y, next.z << 4 | z), 0);
                    }
                }
            }
        }
    }

    // A chunk came again. Where its blocks are not what they were, the light is worked out again
    private void replaceBlocks(Column column, Kinds[] kinds) {
        int[] places = new int[64];
        int count = 0;
        for (int section = 0; section < this.sectionCount; section++) {
            Kinds before = column.kinds[section], now = kinds[section];
            if (before.isUniform() && now.isUniform() && before.get(0) == now.get(0)) {
                continue;
            }
            for (int index = 0; index < SECTION_BLOCKS; index++) {
                if (before.get(index) != now.get(index)) {
                    if (count == places.length) {
                        places = Arrays.copyOf(places, count * 2);
                    }
                    places[count++] = (section << 4 | index >> 8) << 8 | index & 255;
                }
            }
        }
        column.kinds = kinds;
        if (count > 0) {
            this.relight(column, places, count);
        }
    }

    // Blocks of a chunk are of another kind of light than they were: the light they had goes, also where it went
    // through them, the light they give and the light around them comes in again. Each place is the height, then
    // z and x
    private void relight(Column column, int[] places, int count) {
        int baseX = column.x << 4, baseZ = column.z << 4;
        for (int i = 0; i < count; i++) {
            int x = places[i] & 15, y = places[i] >> 8, z = places[i] >> 4 & 15;
            this.restart(column, BLOCK, x, y, z, LightConverter.getEmission(column.getKind(x, y, z)));
        }
        this.darken(BLOCK);
        this.spreadLight(BLOCK);
        if (!this.skyLight) {
            return;
        }

        // Where the light of the sky comes straight down to can have changed
        boolean[] checked = new boolean[256];
        for (int i = 0; i < count; i++) {
            int place = places[i] & 255;
            if (checked[place]) {
                continue;
            }
            checked[place] = true;
            int x = place & 15, z = place >> 4;
            int before = column.lowestSky[place], now = this.findLowestSky(column, x, z);
            column.lowestSky[place] = now;
            for (int y = now; y < before; y++) {
                this.setLight(column, SKY, x, y, z, MAX_LIGHT);
                this.brighter.add(getPlace(baseX + x, y, baseZ + z), 0);
            }
            for (int y = before; y < now; y++) {
                this.setLight(column, SKY, x, y, z, 0);
                this.darker.add(getPlace(baseX + x, y, baseZ + z), MAX_LIGHT);
            }
        }
        for (int i = 0; i < count; i++) {
            int x = places[i] & 15, y = places[i] >> 8, z = places[i] >> 4 & 15;
            this.restart(column, SKY, x, y, z, y >= column.lowestSky[places[i] & 255] ? MAX_LIGHT : 0);
        }
        this.darken(SKY);
        this.spreadLight(SKY);
    }

    // The light of a block that changed starts over from its own: what it had goes from around it, as it may have
    // gone out by a side that stops light now, and the light it has itself and that around it spreads again
    private void restart(Column column, int layer, int x, int y, int z, int own) {
        int before = column.getLight(layer, x, y, z);
        long place = getPlace(column.x << 4 | x, y, column.z << 4 | z);
        this.setLight(column, layer, x, y, z, own);
        if (before > 0) {
            this.darker.add(place, before);
        }
        if (own > 0) {
            this.brighter.add(place, 0);
        }
        this.pullFromAround(column.x << 4 | x, y, column.z << 4 | z);
    }

    // The blocks around a place spread their light into it again
    private void pullFromAround(int x, int y, int z) {
        for (int side = 0; side < LightConverter.SIDES; side++) {
            int nextY = y + SIDE_Y[side];
            if (nextY >= 0 && nextY < this.height) {
                this.brighter.add(getPlace(x + SIDE_X[side], nextY, z + SIDE_Z[side]), 0);
            }
        }
    }

    // Light spreads from the places that are waiting for it, as far as it gets
    private void spreadLight(int layer) {
        while (!this.brighter.isEmpty()) {
            long place = this.brighter.poll();
            int x = getX(place), y = getY(place), z = getZ(place);
            Column column = this.getColumn(x >> 4, z >> 4);
            if (column == null) {
                continue;
            }
            int level = column.getLight(layer, x & 15, y, z & 15);
            if (level <= 1) {
                continue;
            }
            int kind = column.getKind(x & 15, y, z & 15);
            for (int side = 0; side < LightConverter.SIDES; side++) {
                int nextX = x + SIDE_X[side], nextY = y + SIDE_Y[side], nextZ = z + SIDE_Z[side];
                if (nextY < 0 || nextY >= this.height) {
                    continue;
                }
                Column next = nextX >> 4 == column.x && nextZ >> 4 == column.z ? column : this.getColumn(nextX >> 4, nextZ >> 4);
                if (next == null) {
                    continue;
                }
                int current = next.getLight(layer, nextX & 15, nextY, nextZ & 15);
                if (current >= level - 1) {
                    continue;
                }
                int nextKind = next.getKind(nextX & 15, nextY, nextZ & 15);
                int nextLevel = level - LightConverter.getOpacity(nextKind);
                if (nextLevel <= current || LightConverter.occludes(kind, nextKind, side)) {
                    continue;
                }
                this.setLight(next, layer, nextX & 15, nextY, nextZ & 15, nextLevel);
                if (nextLevel > 1) {
                    this.brighter.add(getPlace(nextX, nextY, nextZ), 0);
                }
            }
        }
    }

    // Light that went takes with it the light it gave around, as far as that was less than its own. Light around
    // that is as bright or brighter came from elsewhere and spreads again, as does that of blocks giving light
    private void darken(int layer) {
        while (!this.darker.isEmpty()) {
            long place = this.darker.poll();
            int level = this.darker.lastLevel;
            int x = getX(place), y = getY(place), z = getZ(place);
            for (int side = 0; side < LightConverter.SIDES; side++) {
                int nextX = x + SIDE_X[side], nextY = y + SIDE_Y[side], nextZ = z + SIDE_Z[side];
                if (nextY < 0 || nextY >= this.height) {
                    continue;
                }
                Column next = this.getColumn(nextX >> 4, nextZ >> 4);
                if (next == null) {
                    continue;
                }
                int current = next.getLight(layer, nextX & 15, nextY, nextZ & 15);
                if (current == 0) {
                    continue;
                }
                long nextPlace = getPlace(nextX, nextY, nextZ);
                if (current >= level) {
                    this.brighter.add(nextPlace, 0);
                    continue;
                }
                this.setLight(next, layer, nextX & 15, nextY, nextZ & 15, 0);
                this.darker.add(nextPlace, current);
                int emission = layer == BLOCK ? LightConverter.getEmission(next.getKind(nextX & 15, nextY, nextZ & 15)) : 0;
                if (emission > 0) {
                    this.setLight(next, layer, nextX & 15, nextY, nextZ & 15, emission);
                    this.brighter.add(nextPlace, 0);
                }
            }
        }
    }

    // The java client is told of the light that changed in the chunks it has
    private void tellChanges() {
        if (this.changedColumns.isEmpty()) {
            return;
        }
        for (Column column : this.changedColumns) {
            if (column.changed[SKY] != 0 || column.changed[BLOCK] != 0) {
                this.player.getJavaSession().send(new ClientboundLightUpdatePacket(column.x, column.z,
                        this.getLightData(column, this.skyLight ? column.changed[SKY] : 0, column.changed[BLOCK])));
                column.changed[SKY] = column.changed[BLOCK] = 0;
            }
        }
        this.changedColumns.clear();
    }

    // The light of some sections of a chunk, a bit for each. A section without light is told as empty. The bytes
    // are copied, the packet is written out later while the light goes on changing
    private LightUpdateData getLightData(Column column, long skySections, long blockSections) {
        BitSet skyMask = new BitSet(), blockMask = new BitSet(), emptySkyMask = new BitSet(), emptyBlockMask = new BitSet();
        List<byte[]> skyUpdates = new ArrayList<>(), blockUpdates = new ArrayList<>();
        for (int section = 0; section < this.sectionCount + 2; section++) {
            if (this.skyLight && (skySections >> section & 1) != 0) {
                addSection(column, SKY, section, skyMask, emptySkyMask, skyUpdates);
            }
            if ((blockSections >> section & 1) != 0) {
                addSection(column, BLOCK, section, blockMask, emptyBlockMask, blockUpdates);
            }
        }
        return new LightUpdateData(skyMask, blockMask, emptySkyMask, emptyBlockMask, skyUpdates, blockUpdates);
    }

    private static void addSection(Column column, int layer, int section, BitSet mask, BitSet emptyMask, List<byte[]> updates) {
        byte[] data = column.light[layer][section];
        if (data == null && column.fill[layer][section] == 0) {
            emptyMask.set(section);
            return;
        }
        mask.set(section);
        updates.add(data != null ? data.clone() : FILLED[column.fill[layer][section]]);
    }
}
