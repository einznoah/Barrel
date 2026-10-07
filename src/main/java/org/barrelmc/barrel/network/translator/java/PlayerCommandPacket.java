package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

public class PlayerCommandPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundPlayerCommandPacket packet = (ServerboundPlayerCommandPacket) pk;
        switch (packet.getState()) {
            case LEAVE_BED: {
                PlayerActionPacket playerActionPacket = new PlayerActionPacket();
                playerActionPacket.setAction(PlayerActionType.STOP_SLEEP);
                playerActionPacket.setBlockPosition(Vector3i.ZERO);
                playerActionPacket.setResultPosition(Vector3i.ZERO);
                playerActionPacket.setFace(0);
                playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
                player.getBedrockSession().sendPacket(playerActionPacket);
                break;
            }
            case START_SPRINTING: {
                if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                    PlayerActionPacket playerActionPacket = new PlayerActionPacket();
                    playerActionPacket.setAction(PlayerActionType.START_SPRINT);
                    playerActionPacket.setBlockPosition(Vector3i.ZERO);
                    playerActionPacket.setResultPosition(Vector3i.ZERO);
                    playerActionPacket.setFace(0);
                    playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
                    player.getBedrockSession().sendPacket(playerActionPacket);
                } else {
                    player.getPlayerAuthInputData().add(PlayerAuthInputData.START_SPRINTING);
                    player.setSprinting(true);
                }
                break;
            }
            case STOP_SPRINTING: {
                if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                    PlayerActionPacket playerActionPacket = new PlayerActionPacket();
                    playerActionPacket.setAction(PlayerActionType.STOP_SPRINT);
                    playerActionPacket.setBlockPosition(Vector3i.ZERO);
                    playerActionPacket.setResultPosition(Vector3i.ZERO);
                    playerActionPacket.setFace(0);
                    playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
                    player.getBedrockSession().sendPacket(playerActionPacket);
                } else {
                    player.setSprinting(false);
                    player.getPlayerAuthInputData().add(PlayerAuthInputData.STOP_SPRINTING);
                }
                break;
            }
        }
    }
}
