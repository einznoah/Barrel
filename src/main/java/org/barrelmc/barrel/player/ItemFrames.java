package org.barrelmc.barrel.player;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.java.PlayerActionPacket;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The item frames. A bedrock server has them as blocks that hold the item, a java client as entities that hang on
// the block behind them. Each frame block of the chunks the java client has is shown as an item frame entity, by a
// number that no entity of the server has. A click on it is a click on the block, and a hit on it what a bedrock
// client does when it hits the block: one that holds an item lets go of it, an empty one breaks, as both do for a
// java client after a single hit. This belongs to the thread that translates the packets
public class ItemFrames {

    // The numbers of the entities, far above those a server gives its own
    private static final int FIRST_ENTITY_ID = 1 << 30;
    // The data of a java item frame: the side it faces, its item and how far that is turned
    private static final int JAVA_DIRECTION = 8;
    private static final int JAVA_ITEM = 9;
    private static final int JAVA_ROTATION = 10;
    // A bedrock frame turns its item an eighth at a time, which it tells in degrees
    private static final float ROTATION_STEP = 45;
    private static final int ROTATIONS = 8;
    // Where on the frame a click is
    private static final Vector3f MIDDLE = Vector3f.from(0.5F, 0.5F, 0.5F);
    // A hit on a frame that holds an item ends the tick after: the item comes out at once. An empty frame is broken
    // in as many ticks as the server tells it takes, out of 65535 a tick; one more is waited for, and as long for
    // the server to tell it at all
    private static final int HIT_TICKS = 1;
    private static final int BREAK_WHOLE = 65535;
    private static final int BREAK_WAIT_TICKS = 10;

    private record Frame(int entityId, BlockConverter.BedrockFrame block) {
    }

    private final Player player;
    private final Map<Vector3i, Frame> frames = new HashMap<>();
    private final Map<Integer, Vector3i> positions = new HashMap<>();
    private int nextEntityId = FIRST_ENTITY_ID;
    private boolean started;
    // The frame that is hit, whether it is broken, and the ticks until that is done or waited for
    private Vector3i hit;
    private boolean breaking;
    private int ticksLeft;
    private int waitLeft;

    public ItemFrames(Player player) {
        this.player = player;
    }

    // A block is another one: a frame is shown, or one that is gone taken away
    public void blockChanged(Vector3i position, int bedrockBlockId) {
        BlockConverter.BedrockFrame block = BlockConverter.getBedrockFrame(bedrockBlockId, this.player.getStartGamePacketCache().isBlockNetworkIdsHashed());
        if (block != null) {
            this.show(position, block);
        } else if (this.frames.containsKey(position)) {
            this.remove(List.of(position));
        }
    }

    // What the frame holds changed: its item, or how that is turned
    public void dataChanged(Vector3i position, NbtMap data) {
        Frame frame = this.frames.get(position);
        if (frame != null) {
            this.sendData(frame, data);
        }
    }

    // The chunk was sent to the java client, with these frame blocks in it
    public void chunkSent(List<Vector3i> frameBlocks) {
        boolean hashed = this.player.getStartGamePacketCache().isBlockNetworkIdsHashed();
        for (Vector3i position : frameBlocks) {
            BlockConverter.BedrockFrame block = BlockConverter.getBedrockFrame(this.player.getBedrockBlocks().getBlock(position), hashed);
            if (block != null) {
                this.show(position, block);
            }
        }
    }

    // A chunk is gone or comes again, the frames that were in it go
    public void forgetChunk(int chunkX, int chunkZ) {
        List<Vector3i> gone = new ArrayList<>();
        for (Vector3i position : this.frames.keySet()) {
            if (position.getX() >> 4 == chunkX && position.getZ() >> 4 == chunkZ) {
                gone.add(position);
            }
        }
        if (!gone.isEmpty()) {
            this.remove(gone);
        }
    }

    // The java client made a new world, which has none of the entities
    public void clear() {
        this.frames.clear();
        this.positions.clear();
        this.hit = null;
    }

    // The java client clicked on an entity: if that is a frame, the block is clicked on
    public boolean interact(int entityId) {
        Vector3i position = this.positions.get(entityId);
        if (position == null) {
            return false;
        }
        this.player.getInventory().useItemOn(position, this.frames.get(position).block().facing(), false, MIDDLE);
        return true;
    }

    // The java client hit an entity: if that is a frame, the block is hit
    public boolean attack(int entityId) {
        Vector3i position = this.positions.get(entityId);
        if (position == null) {
            return false;
        }

        Direction face = this.frames.get(position).block().facing();
        boolean holdsItem = getItem(this.player.getBedrockBlocks().getBlockEntityData(position)) != null;
        if (!holdsItem && this.player.getGameMode() == GameType.CREATIVE) {
            PlayerActionPacket.dig(this.player, PlayerAction.FINISH_DIGGING, position, face);
            return true;
        }
        PlayerActionPacket.dig(this.player, PlayerAction.START_DIGGING, position, face);
        this.hit = position;
        this.breaking = !holdsItem;
        this.ticksLeft = holdsItem ? HIT_TICKS : -1;
        this.waitLeft = BREAK_WAIT_TICKS;
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tick);
        }
        return true;
    }

    // The server tells how fast a block is broken that someone started to break
    public void breakStarted(Vector3i position, int speed) {
        if (this.breaking && position.equals(this.hit) && this.ticksLeft < 0 && speed > 0) {
            this.ticksLeft = (BREAK_WHOLE + speed - 1) / speed + 1;
        }
    }

    private void tick() {
        // The player went on to break something else in the meantime
        if (this.hit == null || !this.hit.equals(this.player.getDiggingPosition()) || this.player.getDiggingStatus() != PlayerActionType.START_BREAK) {
            this.hit = null;
            return;
        }
        Frame frame = this.frames.get(this.hit);
        // A frame that is broken whose speed of breaking the server does not tell, or that is gone, is let go of
        boolean waited = this.ticksLeft < 0 && --this.waitLeft <= 0;
        if (frame == null || waited || (this.ticksLeft >= 0 && --this.ticksLeft <= 0)) {
            boolean broken = frame != null && this.breaking && !waited;
            PlayerActionPacket.dig(this.player, broken ? PlayerAction.FINISH_DIGGING : PlayerAction.CANCEL_DIGGING, this.hit, frame == null ? Direction.DOWN : frame.block().facing());
            this.hit = null;
        }
    }

    private void show(Vector3i position, BlockConverter.BedrockFrame block) {
        Frame frame = this.frames.get(position);
        if (frame != null && frame.block().equals(block)) {
            return;
        }
        if (frame != null) {
            this.remove(List.of(position));
        }

        // A java client is told the block the frame is in, and works out itself where on it the frame hangs
        frame = new Frame(this.nextEntityId++, block);
        this.frames.put(position, frame);
        this.positions.put(frame.entityId(), position);
        this.player.getJavaSession().send(new ClientboundAddEntityPacket(frame.entityId(), UUID.randomUUID(), block.glow() ? EntityType.GLOW_ITEM_FRAME : EntityType.ITEM_FRAME,
                block.facing(), position.getX(), position.getY(), position.getZ(), 0, 0, 0));
        this.sendData(frame, this.player.getBedrockBlocks().getBlockEntityData(position));
    }

    private void remove(List<Vector3i> gone) {
        int[] entityIds = new int[gone.size()];
        for (int i = 0; i < gone.size(); i++) {
            Frame frame = this.frames.remove(gone.get(i));
            this.positions.remove(frame.entityId());
            entityIds[i] = frame.entityId();
        }
        this.player.getJavaSession().send(new ClientboundRemoveEntitiesPacket(entityIds));
    }

    private void sendData(Frame frame, NbtMap data) {
        int rotation = data == null ? 0 : Math.floorMod(Math.round(data.getFloat("ItemRotation", 0) / ROTATION_STEP), ROTATIONS);
        this.player.getJavaSession().send(new ClientboundSetEntityDataPacket(frame.entityId(), new EntityMetadata<?, ?>[]{
                new ObjectEntityMetadata<>(JAVA_DIRECTION, MetadataTypes.DIRECTION, frame.block().facing()),
                new ObjectEntityMetadata<>(JAVA_ITEM, MetadataTypes.ITEM_STACK, getItem(data)),
                new IntEntityMetadata(JAVA_ROTATION, MetadataTypes.INT, rotation)
        }));
    }

    // The item a frame holds as the server tells it, null for none
    private static ItemStack getItem(NbtMap data) {
        NbtMap item = data == null ? null : data.getCompound("Item", null);
        if (item == null || item.getString("Name", "").isEmpty() || item.getByte("Count", (byte) 1) <= 0) {
            return null;
        }
        return ItemConverter.bedrockToJavaItem(ItemData.builder()
                .definition(new SimpleItemDefinition(item.getString("Name"), 0, false))
                .damage(item.getShort("Damage", (short) 0))
                .count(item.getByte("Count", (byte) 1))
                .tag(item.getCompound("tag", null))
                .build());
    }
}
