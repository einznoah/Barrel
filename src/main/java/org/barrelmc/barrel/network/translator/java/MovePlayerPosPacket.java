package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

public class MovePlayerPosPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundMovePlayerPosPacket packet = (ServerboundMovePlayerPosPacket) pk;

        if (player.isImmobile()) {
            player.getJavaSession().send(new ClientboundPlayerPositionPacket(1, player.x, player.y, player.z, 0, 0, 0, player.yaw, player.pitch));
            return;
        }
        // Sent from where the player was before the server put it somewhere else
        if (player.getInput().isHeld()) {
            return;
        }

        player.getInput().setCollisions(packet.isOnGround(), packet.isHorizontalCollision());
        player.setOldPosition(player.getVector3f());
        player.setPosition(packet.getX(), packet.getY(), packet.getZ());
        if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
            MovePlayerPacket movePlayerPacket = new MovePlayerPacket();

            movePlayerPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            movePlayerPacket.setPosition(player.getVector3f());
            movePlayerPacket.setRotation(Vector3f.from(player.getPitch(), player.getYaw(), player.getYaw()));
            movePlayerPacket.setMode(MovePlayerPacket.Mode.NORMAL);
            movePlayerPacket.setOnGround(packet.isOnGround());
            movePlayerPacket.setRidingRuntimeEntityId(0);
            movePlayerPacket.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
            movePlayerPacket.setEntityType(0);

            player.getBedrockSession().sendPacket(movePlayerPacket);
        }
    }
}
