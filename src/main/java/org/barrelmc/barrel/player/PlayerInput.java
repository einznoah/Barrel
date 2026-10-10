package org.barrelmc.barrel.player;

import org.barrelmc.barrel.entity.Entity;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

import java.util.Set;

// What a bedrock client tells the server every tick: where the player is and what it presses. A server of mojang
// moves the player itself from what is pressed, and only takes where the client says it is when that is close to
// where the server got to. A player the server is not told the keys of stays where it joined for the server,
// whatever the client shows. This belongs to the thread that translates the packets
public class PlayerInput {

    // What a player loses of its speed in a tick, on the ground and in the air, and what it falls faster by
    private static final float GROUND_FRICTION = 0.6F;
    private static final float DRAG = 0.91F;
    private static final float GRAVITY = 0.08F;
    private static final float SNEAKING_SPEED = 0.3F;
    // A java client tells when it ends a tick, 20 times a second. The proxy ticks for one that does not
    private static final long JAVA_TICK_NANOS = 250_000_000L;
    // The teleport the java client is told about when the server puts the player somewhere else
    private static final int CORRECTION_TELEPORT_ID = 2;
    // The numbers the java client is told with where the player is and has to answer, from this one on
    private static final int FIRST_TELEPORT_ID = 100;
    // A java server tells its client again where the player is when the client did not answer for this many ticks
    private static final int TELEPORT_AGAIN_TICKS = 20;
    // How long the server is waited for to tell that the player has arrived in another dimension
    private static final int DIMENSION_CHANGE_TICKS = 200;
    // The server putting the player back is told of when it did so this often in a minute
    private static final int CORRECTIONS_TOLD = 20;
    private static final long CORRECTIONS_MILLIS = 60_000;
    // For how many ticks it is kept where the server was told the player is, more than a server looks back
    private static final int TOLD_TICKS = 128;
    // How far the server may have the player from where it was told for that to be taken as the two having moved
    // it not quite alike, and not as the server having put the player somewhere
    private static final float MOVED_UNALIKE = 4;

    private final Player player;
    private boolean started;
    private long tick;
    private long lastJavaTick;

    // What is pressed
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean shift;
    private boolean sprint;
    private boolean lastJump;
    private boolean lastShift;

    private boolean onGround;
    private boolean lastOnGround;
    private boolean horizontalCollision;
    private boolean teleported;
    private boolean wasRiding;
    private boolean wasSleeping;
    // Where the server said it has the player while it sleeps, null when it does not sleep or nothing was said
    private Vector3f asleepAt;
    private Vector3f lastPosition;

    // The number the java client was last told with where the player is, and for how long it has not answered. What
    // it sends of where the player is until it answers, it sent from where the player was before
    private int teleports = FIRST_TELEPORT_ID;
    private int awaitedTeleport;
    private int awaitedTicks;
    private int dimensionChangeTicks;
    private int corrections;
    private long correctionsSince;
    // Where the server was told the eyes of the player are, for the last ticks: what the server corrects is where
    // the player was at one of them
    private final long[] toldTicks = new long[TOLD_TICKS];
    private final Vector3f[] told = new Vector3f[TOLD_TICKS];

    public PlayerInput(Player player) {
        this.player = player;
    }

    public void setKeys(ServerboundPlayerInputPacket packet) {
        this.forward = packet.isForward();
        this.backward = packet.isBackward();
        this.left = packet.isLeft();
        this.right = packet.isRight();
        this.jump = packet.isJump();
        this.shift = packet.isShift();
        this.sprint = packet.isSprint();
    }

    public void setCollisions(boolean onGround, boolean horizontalCollision) {
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
    }

    // The server put the player somewhere else, it waits to be told that the client knows
    public void setTeleported() {
        this.teleported = true;
        this.lastPosition = null;
    }

    // Puts the java client where the player is for the proxy, which is where the server put it
    public void teleportJava() {
        this.teleports = this.teleports == Integer.MAX_VALUE ? FIRST_TELEPORT_ID : this.teleports + 1;
        this.awaitedTeleport = this.teleports;
        this.awaitedTicks = 0;
        this.lastPosition = null;
        java.util.Arrays.fill(this.told, null);
        this.player.getJavaSession().send(new ClientboundPlayerPositionPacket(this.awaitedTeleport, this.player.x, this.player.y, this.player.z, 0, 0, 0, this.player.getYaw(), this.player.getPitch()));
    }

    public void acceptTeleport(int teleport) {
        if (teleport == this.awaitedTeleport) {
            this.awaitedTeleport = 0;
        }
    }

    // Whether the player is where the server put it, whatever the java client sends of where it is: the client has
    // not said yet that it is there, or the server is taking the player to another dimension
    public boolean isHeld() {
        return this.awaitedTeleport != 0 || this.player.isChangingDimension() || this.player.getRiding().isRiding();
    }

    public void startDimensionChange() {
        this.dimensionChangeTicks = 0;
    }

    public void start(long tick) {
        if (!this.started) {
            this.started = true;
            this.tick = tick;
            this.player.runEveryTick(this::proxyTick);
        }
    }

    // The java client ended a tick: it has sent where the player is now and what is pressed
    public void javaTick() {
        if (this.started) {
            this.lastJavaTick = System.nanoTime();
            this.send();
        }
    }

    private void proxyTick() {
        if (System.nanoTime() - this.lastJavaTick > JAVA_TICK_NANOS) {
            this.send();
        }
    }

    // The server did not get to where the client says the player is, the player is where the server says. Also
    // when that is told late: left aside, the player would stay somewhere else for the server than for the client.
    // The server tells where it had the player at a tick that has passed, and has moved the player on from there by
    // what was pressed since. A bedrock client goes back to that tick and moves again as it did. The java client is
    // moved by as much as the server was off at that tick, which comes to the same where nothing is in the way: put
    // where the server had the player then, it would be behind the server by what it moved since, far enough to
    // be corrected again and again while it moves
    public void correct(Vector3f position, boolean onGround, long tick) {
        long now = System.currentTimeMillis();
        if (now - this.correctionsSince > CORRECTIONS_MILLIS) {
            this.correctionsSince = now;
            this.corrections = 0;
        }

        int place = (int) Math.floorMod(tick, (long) TOLD_TICKS);
        // A player that rides is where its vehicle is, which the server moves or corrects by itself
        if (this.player.getRiding().isRiding()) {
            return;
        }
        // A player that sleeps is in its bed, where the java client was put when it lay down. The server tells
        // where it has a sleeping player by another point than the eyes of one that stands: taken for those, the
        // java client would be put into the ground under the bed, and fall through it. The server is told back
        // what it said, so that it stops saying it
        if (this.player.getSelf().isSleeping()) {
            this.asleepAt = position;
            return;
        }
        this.settleSleep();
        Vector3f toldThen = this.toldTicks[place] == tick ? this.told[place] : null;
        Vector3f off = toldThen == null ? null : position.sub(toldThen);
        if (off != null && off.length() <= MOVED_UNALIKE) {
            this.player.setPosition(this.player.x + off.getX(), this.player.y + off.getY(), this.player.z + off.getZ());
            this.count(onGround);
            // Told as how far to move from where the client has the player by now, which the proxy knows a little late
            this.player.getJavaSession().send(new ClientboundPlayerPositionPacket(CORRECTION_TELEPORT_ID, off.getX(), off.getY(), off.getZ(), 0, 0, 0, 0, 0,
                    PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT, PositionElement.X_ROT, PositionElement.DELTA_X, PositionElement.DELTA_Y, PositionElement.DELTA_Z));
            return;
        }

        this.player.setPosition(position.getX(), position.getY() - Entity.PLAYER_EYE_HEIGHT, position.getZ());
        this.count(onGround);
        // Where the player looks and how fast it is stay as they are
        this.player.getJavaSession().send(new ClientboundPlayerPositionPacket(CORRECTION_TELEPORT_ID, this.player.x, this.player.y, this.player.z, 0, 0, 0, 0, 0,
                PositionElement.Y_ROT, PositionElement.X_ROT, PositionElement.DELTA_X, PositionElement.DELTA_Y, PositionElement.DELTA_Z));
    }

    // Lying down and getting up put the player somewhere else, and a java client that wakes its player up stands
    // it next to the bed by itself: what the server corrects next is where it has the player, not how far the two
    // were apart while the player slept
    private void settleSleep() {
        boolean sleeping = this.player.getSelf().isSleeping();
        if (sleeping != this.wasSleeping) {
            this.wasSleeping = sleeping;
            this.lastPosition = null;
            java.util.Arrays.fill(this.told, null);
        }
    }

    private void count(boolean onGround) {
        if (++this.corrections == CORRECTIONS_TOLD) {
            System.out.println("The server put the player back " + CORRECTIONS_TOLD + " times within a minute, the last time to " + this.player.getFloorX() + " " + this.player.getFloorY() + " " + this.player.getFloorZ()
                    + ": it does not get to where the java client says the player is [player " + this.player.getUsername() + "]");
        }
        this.onGround = onGround;
        this.lastPosition = null;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }

    private void send() {
        if (!this.player.getBedrockSession().isConnected()) {
            return;
        }
        // While the server takes the player to another dimension a bedrock client shows a loading screen, and goes on
        // telling the server every tick where the player is. A server that never tells that the player has arrived
        // is not waited for without end: the player would stay where it is for the server, whatever the client does
        if (this.player.isChangingDimension() && ++this.dimensionChangeTicks >= DIMENSION_CHANGE_TICKS) {
            System.out.println("The server did not tell that the player has arrived in the other dimension, the proxy goes on without that [player " + this.player.getUsername() + "]");
            org.barrelmc.barrel.network.translator.bedrock.ChangeDimensionPacket.finish(this.player, true);
        }
        if (this.awaitedTeleport != 0 && ++this.awaitedTicks >= TELEPORT_AGAIN_TICKS) {
            this.teleportJava();
        }
        this.tick++;

        // A player that rides is where its seat is: a java client says nothing of where it is while it rides. It
        // gets off when the sneak key is pressed, which a java client leaves to its server
        if (this.shift && !this.lastShift) {
            this.player.getRiding().leave();
        }
        Vector3f seat = this.player.getRiding().getSeat();
        if (seat != null) {
            this.player.setPosition(seat.getX(), seat.getY() - Entity.PLAYER_EYE_HEIGHT, seat.getZ());
        }
        this.settleSleep();
        if ((seat != null) != this.wasRiding) {
            // Getting on or off puts the player somewhere else: what the server corrects next is where it has the
            // player, not how far the two were apart before
            this.wasRiding = seat != null;
            this.lastPosition = null;
            java.util.Arrays.fill(this.told, null);
        }

        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        Set<PlayerAuthInputData> inputData = packet.getInputData();
        inputData.addAll(this.player.getPlayerAuthInputData());
        this.player.getPlayerAuthInputData().clear();
        packet.getPlayerActions().addAll(this.player.getPlayerAuthInputActions());
        this.player.getPlayerAuthInputActions().clear();

        inputData.add(PlayerAuthInputData.BLOCK_BREAKING_DELAY_ENABLED);
        if (this.onGround) {
            inputData.add(PlayerAuthInputData.VERTICAL_COLLISION);
        }
        if (this.horizontalCollision) {
            inputData.add(PlayerAuthInputData.HORIZONTAL_COLLISION);
        }
        if (this.teleported) {
            this.teleported = false;
            inputData.add(PlayerAuthInputData.HANDLE_TELEPORT);
        }

        if (this.forward) {
            inputData.add(PlayerAuthInputData.UP);
        }
        if (this.backward) {
            inputData.add(PlayerAuthInputData.DOWN);
        }
        if (this.left) {
            inputData.add(PlayerAuthInputData.LEFT);
        }
        if (this.right) {
            inputData.add(PlayerAuthInputData.RIGHT);
        }
        if (this.jump) {
            inputData.add(PlayerAuthInputData.JUMP_DOWN);
            inputData.add(PlayerAuthInputData.JUMPING);
            inputData.add(PlayerAuthInputData.WANT_UP);
            inputData.add(PlayerAuthInputData.JUMP_CURRENT_RAW);
            if (this.lastOnGround) {
                inputData.add(PlayerAuthInputData.START_JUMPING);
            }
        }
        if (this.jump != this.lastJump) {
            inputData.add(this.jump ? PlayerAuthInputData.JUMP_PRESSED_RAW : PlayerAuthInputData.JUMP_RELEASED_RAW);
        }
        if (this.shift) {
            inputData.add(PlayerAuthInputData.SNEAK_DOWN);
            inputData.add(PlayerAuthInputData.SNEAKING);
            inputData.add(PlayerAuthInputData.WANT_DOWN);
            inputData.add(PlayerAuthInputData.SNEAK_CURRENT_RAW);
        }
        if (this.shift != this.lastShift) {
            inputData.add(this.shift ? PlayerAuthInputData.SNEAK_PRESSED_RAW : PlayerAuthInputData.SNEAK_RELEASED_RAW);
            inputData.add(this.shift ? PlayerAuthInputData.START_SNEAKING : PlayerAuthInputData.STOP_SNEAKING);
        }
        if (this.sprint) {
            inputData.add(PlayerAuthInputData.SPRINT_DOWN);
        }
        if (this.sprint || this.player.isSprinting()) {
            inputData.add(PlayerAuthInputData.SPRINTING);
        }

        if (this.player.getDiggingStatus() == PlayerActionType.START_BREAK) {
            inputData.add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);

            PlayerBlockActionData blockActionData = new PlayerBlockActionData();
            // A server that breaks the block itself is told that the player goes on, one that does not how far it is
            blockActionData.setAction(this.player.getStartGamePacketCache().isServerAuthoritativeBlockBreaking() ? PlayerActionType.BLOCK_CONTINUE_DESTROY : PlayerActionType.CONTINUE_BREAK);
            blockActionData.setBlockPosition(this.player.getDiggingPosition());
            blockActionData.setFace(this.player.getDiggingFace().ordinal());
            packet.getPlayerActions().add(blockActionData);
        }

        // Two keys that are against each other are none, and two that are not make the player no faster than one
        float sideways = this.left == this.right ? 0 : this.left ? 1 : -1;
        float ahead = this.forward == this.backward ? 0 : this.forward ? 1 : -1;
        if (sideways != 0 && ahead != 0) {
            sideways *= (float) Math.sqrt(0.5);
            ahead *= (float) Math.sqrt(0.5);
        }
        Vector2f moveVector = Vector2f.from(sideways, ahead);

        Vector3f position = this.player.getVector3f();
        if (!this.player.getSelf().isSleeping()) {
            this.asleepAt = null;
        } else if (this.asleepAt != null) {
            position = this.asleepAt;
        }
        Vector3f moved = this.lastPosition == null ? Vector3f.ZERO : position.sub(this.lastPosition);
        // How fast the player is after this tick, which is what a bedrock client says of itself
        Vector3f delta = moved;
        if (!this.player.isFlying() && !this.player.isChangingDimension() && seat == null) {
            float friction = (this.onGround ? GROUND_FRICTION : 1) * DRAG;
            delta = Vector3f.from(moved.getX() * 0.98F * friction, (moved.getY() - GRAVITY) * 0.98F, moved.getZ() * 0.98F * friction);
        }

        float yaw = wrapDegrees(this.player.getYaw());
        packet.setPosition(position);
        packet.setRotation(Vector3f.from(this.player.getPitch(), yaw, yaw));
        packet.setMotion(this.shift ? moveVector.mul(SNEAKING_SPEED) : moveVector);
        packet.setRawMoveVector(moveVector);
        packet.setAnalogMoveVector(Vector2f.ZERO);
        packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
        packet.setInputMode(InputMode.MOUSE);
        packet.setPlayMode(ClientPlayMode.SCREEN);
        packet.setVrGazeDirection(null);
        packet.setInteractRotation(Vector2f.from(this.player.getPitch(), yaw));
        packet.setTick(this.tick);
        packet.setDelta(delta);
        packet.setCameraOrientation(this.player.getDirectionVector());
        packet.setItemStackRequest(null);
        this.player.getBedrockSession().sendPacketImmediately(packet);

        int place = (int) Math.floorMod(this.tick, (long) TOLD_TICKS);
        this.toldTicks[place] = this.tick;
        this.told[place] = position;

        this.lastPosition = position;
        this.lastOnGround = this.onGround;
        this.lastJump = this.jump;
        this.lastShift = this.shift;

        if (this.player.getInventory().tickItemUse()) {
            this.player.getInventory().finishUsingItem();
        }
    }
}
