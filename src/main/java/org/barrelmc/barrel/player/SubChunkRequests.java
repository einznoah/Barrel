package org.barrelmc.barrel.player;

import io.netty.buffer.ByteBuf;
import org.barrelmc.barrel.network.translator.bedrock.LevelChunkPacket;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// A server of mojang sends a chunk without its blocks and waits to be asked for them, one sub chunk at a time.
// A java client takes a chunk as a whole, so a chunk is kept here until all of its sub chunks are there.
// This belongs to the thread that translates the packets
public class SubChunkRequests {

    // More sub chunks than this are not asked for with one packet, and there is one packet a tick
    private static final int MAX_PER_PACKET = 256;
    // More sub chunks than this are not waited for at a time. A server answers all it was asked for in a tick at
    // once, and what is more than the bedrock connection takes at once is lost as a whole
    private static final int MAX_WAITED_FOR = 512;
    // Where a sub chunk is, is told as a byte from the first one of a packet
    private static final int MAX_OFFSET = 127;
    // The ticks after which a sub chunk is asked for again when no answer came
    private static final int ANSWER_TICKS = 60;
    // The ticks after which a sub chunk is asked for again when the server did not have it yet
    private static final int RETRY_TICKS = 10;
    // The ticks after which a chunk is sent as it is, when the server does not send the rest of it
    private static final int GIVE_UP_TICKS = 600;

    private final Player player;
    private final Map<Long, PendingChunk> chunks = new HashMap<>();
    // The sub chunks that are to be asked for
    private final ArrayDeque<Vector3i> queue = new ArrayDeque<>();
    // The sub chunks the server did not have, they are asked for again a little later
    private final List<Vector3i> retries = new ArrayList<>();
    private int dimension;
    private long tick;
    private boolean started;

    private static class PendingChunk {
        private final int x;
        private final int z;
        private final ChunkSection[] sections;
        private final BedrockBlocks.Column bedrockBlocks;
        // The heights of the sub chunks that are not there yet
        private final Set<Integer> missing = new HashSet<>();
        // The tick each of those was asked for that no answer came for yet
        private final Map<Integer, Long> asked = new HashMap<>();
        private final long since;

        private PendingChunk(int x, int z, ChunkSection[] sections, BedrockBlocks.Column bedrockBlocks, long since) {
            this.x = x;
            this.z = z;
            this.sections = sections;
            this.bedrockBlocks = bedrockBlocks;
            this.since = since;
        }
    }

    public SubChunkRequests(Player player) {
        this.player = player;
    }

    private static long getKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    // Only the bedrock overworld goes below y 0
    private static int getFirstSubChunk(int dimension) {
        return dimension == 0 ? -4 : 0;
    }

    private static int getSubChunkCount(int dimension) {
        return dimension == 0 ? 24 : dimension == 1 ? 8 : 16;
    }

    // A chunk came without its sub chunks. The limit is the last one that is not air, counted from the lowest,
    // there is none when it is below 0
    public void request(int chunkX, int chunkZ, int dimension, int limit, ChunkSection[] sections, BedrockBlocks.Column bedrockBlocks) {
        if (dimension != this.dimension) {
            this.clear();
            this.dimension = dimension;
        }

        // The chunks the player has left behind are not waited for anymore
        int reach = this.player.getRenderDistance() * 2 + 8;
        this.chunks.values().removeIf(chunk -> Math.abs(chunk.x - chunkX) > reach || Math.abs(chunk.z - chunkZ) > reach);

        int first = getFirstSubChunk(dimension);
        int height = getSubChunkCount(dimension);
        int count = limit < 0 ? height : Math.max(1, Math.min(limit + 1, height));

        PendingChunk chunk = new PendingChunk(chunkX, chunkZ, sections, bedrockBlocks, this.tick);
        for (int y = first; y < first + count; y++) {
            chunk.missing.add(y);
            this.queue.add(Vector3i.from(chunkX, y, chunkZ));
        }
        this.chunks.put(getKey(chunkX, chunkZ), chunk);
    }

    // The player has spawned, the server answers from now on
    public void start() {
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tick);
        }
    }

    public void clear() {
        this.chunks.clear();
        this.queue.clear();
        this.retries.clear();
    }

    private void tick() {
        this.tick++;
        if (this.chunks.isEmpty()) {
            this.queue.clear();
            this.retries.clear();
            return;
        }
        if (this.tick % RETRY_TICKS == 0) {
            this.queue.addAll(this.retries);
            this.retries.clear();
        }

        int waitedFor = 0;
        for (Iterator<PendingChunk> iterator = this.chunks.values().iterator(); iterator.hasNext(); ) {
            PendingChunk chunk = iterator.next();
            if (this.tick - chunk.since > GIVE_UP_TICKS) {
                iterator.remove();
                chunk.bedrockBlocks.joinDoors(chunk.sections);
                LevelChunkPacket.sendChunk(this.player, chunk.x, chunk.z, chunk.sections, chunk.bedrockBlocks.getJavaBlockEntities(chunk.sections.length));
                continue;
            }
            // The sub chunks no answer came for are asked for again, before those further away
            for (Iterator<Map.Entry<Integer, Long>> asked = chunk.asked.entrySet().iterator(); asked.hasNext(); ) {
                Map.Entry<Integer, Long> entry = asked.next();
                if (this.tick - entry.getValue() > ANSWER_TICKS) {
                    asked.remove();
                    this.queue.addFirst(Vector3i.from(chunk.x, entry.getKey(), chunk.z));
                }
            }
            waitedFor += chunk.asked.size();
        }

        this.send(Math.min(MAX_PER_PACKET, MAX_WAITED_FOR - waitedFor));
    }

    private void send(int count) {
        Vector3i base = null;
        List<Vector3i> offsets = new ArrayList<>();
        List<Vector3i> tooFar = new ArrayList<>();
        while (!this.queue.isEmpty() && offsets.size() < count) {
            Vector3i position = this.queue.poll();
            PendingChunk chunk = this.chunks.get(getKey(position.getX(), position.getZ()));
            if (chunk == null || !chunk.missing.contains(position.getY()) || chunk.asked.containsKey(position.getY())) {
                continue;
            }
            if (base == null) {
                base = Vector3i.from(position.getX(), 0, position.getZ());
            }

            Vector3i offset = position.sub(base);
            if (Math.abs(offset.getX()) > MAX_OFFSET || Math.abs(offset.getZ()) > MAX_OFFSET) {
                tooFar.add(position);
                continue;
            }
            chunk.asked.put(position.getY(), this.tick);
            offsets.add(offset);
        }
        this.queue.addAll(tooFar);
        if (offsets.isEmpty()) {
            return;
        }

        SubChunkRequestPacket requestPacket = new SubChunkRequestPacket();
        requestPacket.setDimension(this.dimension);
        requestPacket.setSubChunkPosition(base);
        requestPacket.setPositionOffsets(offsets);
        this.player.getBedrockSession().sendPacket(requestPacket);
    }

    public void receive(SubChunkPacket packet) {
        if (packet.getDimension() != this.dimension) {
            return;
        }

        Vector3i center = packet.getCenterPosition();
        boolean hashedBlockIds = this.player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        int minSection = ProxyServer.getInstance().getDimension(this.dimension).minSection();
        for (SubChunkData subChunk : packet.getSubChunks()) {
            Vector3i position = center.add(subChunk.getPosition());
            long key = getKey(position.getX(), position.getZ());
            PendingChunk chunk = this.chunks.get(key);
            if (chunk == null || !chunk.missing.contains(position.getY())) {
                continue;
            }
            chunk.asked.remove(position.getY());

            switch (subChunk.getResult()) {
                case SUCCESS:
                    ByteBuf data = subChunk.getData();
                    if (data != null && data.isReadable()) {
                        try {
                            LevelChunkPacket.readSubChunk(data, chunk.sections, position.getY() - minSection, hashedBlockIds, chunk.bedrockBlocks);
                            // Behind the blocks of a sub chunk is what they hold
                            LevelChunkPacket.readBlockEntityData(data, chunk.bedrockBlocks);
                        } catch (RuntimeException | java.io.IOException e) {
                            // What was read of it stays, as a bedrock client does it
                        }
                    }
                    break;
                case UNDEFINED:
                case CHUNK_NOT_FOUND:
                case PLAYER_NOT_FOUND:
                    // The server does not have it yet
                    this.retries.add(position);
                    continue;
                default:
                    // It is air, or there is nothing at this height
                    break;
            }

            chunk.missing.remove(position.getY());
            if (chunk.missing.isEmpty()) {
                this.chunks.remove(key);
                chunk.bedrockBlocks.joinDoors(chunk.sections);
                LevelChunkPacket.sendChunk(this.player, chunk.x, chunk.z, chunk.sections, chunk.bedrockBlocks.getJavaBlockEntities(chunk.sections.length));
            }
        }
    }
}
