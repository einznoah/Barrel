package org.barrelmc.barrel.player;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

// A server of mojang sends a chunk without its blocks and waits to be asked for them, one sub chunk at a time.
// A java client takes a chunk as a whole, so a chunk is kept here until all of its sub chunks are there.
// This belongs to the thread that translates the packets
public class SubChunkRequests {

    // More sub chunks than this are not asked for with one packet
    private static final int MAX_PER_PACKET = 256;
    // Where a sub chunk is, is told as a byte from the first one of a packet
    private static final int MAX_OFFSET = 127;
    // How often the sub chunks of a chunk are asked for again when the server does not have them yet
    private static final int MAX_TRIES = 40;

    private final Player player;
    private final Map<Long, PendingChunk> chunks = new HashMap<>();
    // The sub chunks that are to be asked for
    private final ArrayDeque<Vector3i> queue = new ArrayDeque<>();
    // The sub chunks the server did not have, they are asked for again a little later
    private final List<Vector3i> retries = new ArrayList<>();
    private int dimension;
    // A server does not answer before the player has spawned
    private boolean started;
    // Whether there is something to ask for again, read by the thread that ticks
    @Getter
    private volatile boolean waiting;

    private static class PendingChunk {
        private final ChunkSection[] sections;
        // The heights of the sub chunks that are not there yet, and of those that were asked for
        private final Set<Integer> missing = new HashSet<>();
        private final Set<Integer> asked = new HashSet<>();
        private int tries;

        private PendingChunk(ChunkSection[] sections) {
            this.sections = sections;
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
    public void request(int chunkX, int chunkZ, int dimension, int limit, ChunkSection[] sections) {
        if (dimension != this.dimension) {
            this.clear();
            this.dimension = dimension;
        }

        // The chunks the player has left behind are not waited for anymore
        int reach = this.player.getRenderDistance() * 2 + 8;
        this.chunks.keySet().removeIf(key -> Math.abs((int) (key >> 32) - chunkX) > reach || Math.abs((int) (long) key - chunkZ) > reach);

        int first = getFirstSubChunk(dimension);
        int height = getSubChunkCount(dimension);
        int count = limit < 0 ? height : Math.max(1, Math.min(limit + 1, height));

        PendingChunk chunk = new PendingChunk(sections);
        for (int y = first; y < first + count; y++) {
            chunk.missing.add(y);
            this.queue.add(Vector3i.from(chunkX, y, chunkZ));
        }
        this.chunks.put(getKey(chunkX, chunkZ), chunk);
        this.send();
    }

    // The player has spawned, the server answers from now on
    public void start() {
        this.started = true;
        this.send();
    }

    public void clear() {
        this.chunks.clear();
        this.queue.clear();
        this.retries.clear();
        this.waiting = false;
    }

    // Asks again for what the server did not have
    public void tick() {
        this.waiting = false;
        this.queue.addAll(this.retries);
        this.retries.clear();
        this.send();
    }

    private void send() {
        if (!this.started) {
            return;
        }

        while (!this.queue.isEmpty()) {
            Vector3i base = null;
            List<Vector3i> offsets = new ArrayList<>();
            List<Vector3i> tooFar = new ArrayList<>();
            while (!this.queue.isEmpty() && offsets.size() < MAX_PER_PACKET) {
                Vector3i position = this.queue.poll();
                PendingChunk chunk = this.chunks.get(getKey(position.getX(), position.getZ()));
                if (chunk == null || !chunk.missing.contains(position.getY()) || chunk.asked.contains(position.getY())) {
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
                chunk.asked.add(position.getY());
                offsets.add(offset);
            }
            this.queue.addAll(tooFar);
            if (base == null) {
                return;
            }

            SubChunkRequestPacket requestPacket = new SubChunkRequestPacket();
            requestPacket.setDimension(this.dimension);
            requestPacket.setSubChunkPosition(base);
            requestPacket.setPositionOffsets(offsets);
            this.player.getBedrockSession().sendPacket(requestPacket);
        }
    }

    public void receive(SubChunkPacket packet) {
        if (packet.getDimension() != this.dimension) {
            return;
        }

        Vector3i center = packet.getCenterPosition();
        boolean hashedBlockIds = this.player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        int minSection = ProxyServer.getInstance().getOverworldMinSection();
        for (SubChunkData subChunk : packet.getSubChunks()) {
            Vector3i position = center.add(subChunk.getPosition());
            long key = getKey(position.getX(), position.getZ());
            PendingChunk chunk = this.chunks.get(key);
            if (chunk == null || !chunk.missing.contains(position.getY())) {
                continue;
            }

            switch (subChunk.getResult()) {
                case SUCCESS:
                    ByteBuf data = subChunk.getData();
                    if (data != null && data.isReadable()) {
                        try {
                            LevelChunkPacket.readSubChunk(data, chunk.sections, position.getY() - minSection, hashedBlockIds);
                        } catch (RuntimeException e) {
                            // What was read of it stays, as a bedrock client does it
                        }
                    }
                    break;
                case UNDEFINED:
                case CHUNK_NOT_FOUND:
                case PLAYER_NOT_FOUND:
                    // The server does not have it yet
                    if (chunk.tries++ < MAX_TRIES) {
                        chunk.asked.remove(position.getY());
                        this.retries.add(position);
                        this.waiting = true;
                        continue;
                    }
                    break;
                default:
                    // It is air, or there is nothing at this height
                    break;
            }

            chunk.missing.remove(position.getY());
            if (chunk.missing.isEmpty()) {
                this.chunks.remove(key);
                LevelChunkPacket.sendChunk(this.player, position.getX(), position.getZ(), chunk.sections);
            }
        }
    }
}
