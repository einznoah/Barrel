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
    private Vector3f lastPosition;

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

    // The server did not get to where the client says the player is, the player is where the server says
    public void correct(Vector3f position, boolean onGround, long tick) {
        if (tick > this.tick || tick < this.tick - this.player.getStartGamePacketCache().getRewindHistorySize()) {
            return;
        }

        this.player.setPosition(position.getX(), position.getY() - Entity.PLAYER_EYE_HEIGHT, position.getZ());
        this.onGround = onGround;
        this.lastPosition = null;
        // Where the player looks and how fast it is stay as they are
        this.player.getJavaSession().send(new ClientboundPlayerPositionPacket(CORRECTION_TELEPORT_ID, this.player.x, this.player.y, this.player.z, 0, 0, 0, 0, 0,
                PositionElement.Y_ROT, PositionElement.X_ROT, PositionElement.DELTA_X, PositionElement.DELTA_Y, PositionElement.DELTA_Z));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }

    private void send() {
        // While the server takes the player to another dimension, a bedrock client shows a loading screen
        if (!this.player.getBedrockSession().isConnected() || this.player.isChangingDimension()) {
            return;
        }
        this.tick++;

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
        Vector3f moved = this.lastPosition == null ? Vector3f.ZERO : position.sub(this.lastPosition);
        // How fast the player is after this tick, which is what a bedrock client says of itself
        Vector3f delta = moved;
        if (!this.player.isFlying()) {
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

        this.lastPosition = position;
        this.lastOnGround = this.onGround;
        this.lastJump = this.jump;
        this.lastShift = this.shift;

        if (this.player.getInventory().tickItemUse()) {
            this.player.getInventory().finishUsingItem();
        }
    }
}
