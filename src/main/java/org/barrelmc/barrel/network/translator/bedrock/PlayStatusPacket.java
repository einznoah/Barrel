package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

public class PlayStatusPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket) pk;

        if (packet.getStatus() == org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status.PLAYER_SPAWN) {
            if (player.getStartGamePacketCache().getAuthoritativeMovementMode() != AuthoritativeMovementMode.CLIENT) {
                player.startSendingPlayerInput();
            }

            SetLocalPlayerAsInitializedPacket setLocalPlayerAsInitializedPacket = new SetLocalPlayerAsInitializedPacket();
            setLocalPlayerAsInitializedPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            player.getBedrockSession().sendPacket(setLocalPlayerAsInitializedPacket);

            Vector3f pos = player.getLastServerPosition();
            Vector2f rotation = player.getLastServerRotation();
            player.getJavaSession().send(new ClientboundPlayerPositionPacket(0, pos.getX(), pos.getY() - Entity.PLAYER_EYE_HEIGHT, pos.getZ(), 0, 0, 0, rotation.getY(), rotation.getX()));
        }
    }
}
