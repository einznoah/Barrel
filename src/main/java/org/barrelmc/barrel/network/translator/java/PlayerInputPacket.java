package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

public class PlayerInputPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundPlayerInputPacket packet = (ServerboundPlayerInputPacket) pk;

        // The client no longer sends a player command when it starts or stops sneaking
        if (packet.isShift() == player.isSneaking()) {
            return;
        }

        if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
            PlayerActionPacket playerActionPacket = new PlayerActionPacket();
            playerActionPacket.setAction(packet.isShift() ? PlayerActionType.START_SNEAK : PlayerActionType.STOP_SNEAK);
            playerActionPacket.setBlockPosition(Vector3i.ZERO);
            playerActionPacket.setResultPosition(Vector3i.ZERO);
            playerActionPacket.setFace(0);
            playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            player.getBedrockSession().sendPacket(playerActionPacket);
        } else {
            player.getPlayerAuthInputData().add(packet.isShift() ? PlayerAuthInputData.START_SNEAKING : PlayerAuthInputData.STOP_SNEAKING);
        }
        player.setSneaking(packet.isShift());
    }
}
