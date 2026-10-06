package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

public class PlayStatusPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket) pk;

        if (packet.getStatus() == org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status.LOGIN_SUCCESS) {
            // The proxy keeps no chunks for the server to leave out
            ClientCacheStatusPacket cacheStatusPacket = new ClientCacheStatusPacket();
            cacheStatusPacket.setSupported(false);
            player.getBedrockSession().sendPacket(cacheStatusPacket);
        }

        if (packet.getStatus() == org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket.Status.PLAYER_SPAWN) {
            if (player.getStartGamePacketCache().getAuthoritativeMovementMode() != AuthoritativeMovementMode.CLIENT) {
                player.startSendingPlayerInput();
            }

            if (!player.isSpawned()) {
                player.setSpawned(true);

                // What a bedrock client sends as it comes out of the loading screen it joined with
                InteractPacket interactPacket = new InteractPacket();
                interactPacket.setAction(InteractPacket.Action.MOUSEOVER);
                interactPacket.setRuntimeEntityId(0);
                player.getBedrockSession().sendPacket(interactPacket);

                ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
                loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN);
                player.getBedrockSession().sendPacket(loadingScreenPacket);
            }

            SetLocalPlayerAsInitializedPacket setLocalPlayerAsInitializedPacket = new SetLocalPlayerAsInitializedPacket();
            setLocalPlayerAsInitializedPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            player.getBedrockSession().sendPacket(setLocalPlayerAsInitializedPacket);

            player.getSubChunkRequests().start();

            Vector3f pos = player.getLastServerPosition();
            Vector2f rotation = player.getLastServerRotation();
            player.getJavaSession().send(new ClientboundPlayerPositionPacket(0, pos.getX(), pos.getY() - Entity.PLAYER_EYE_HEIGHT, pos.getZ(), 0, 0, 0, rotation.getY(), rotation.getX()));
        }
    }
}
