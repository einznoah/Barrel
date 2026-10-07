package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;

public class PlayerActionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket) pk;

        // The server has brought the player to another dimension and sent what is there. A bedrock client answers
        // with the same and closes its loading screen, a java client waits to be told that the chunks come
        if (packet.getAction() == PlayerActionType.DIMENSION_CHANGE_SUCCESS && player.isChangingDimension()) {
            player.setChangingDimension(false);

            org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket playerActionPacket = new org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket();
            playerActionPacket.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
            playerActionPacket.setBlockPosition(Vector3i.ZERO);
            playerActionPacket.setResultPosition(Vector3i.ZERO);
            playerActionPacket.setFace(0);
            playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            player.getBedrockSession().sendPacket(playerActionPacket);

            ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
            loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN);
            loadingScreenPacket.setLoadingScreenId(player.getDimensionLoadingScreen());
            player.getBedrockSession().sendPacket(loadingScreenPacket);

            ChangeDimensionPacket.sendPosition(player);
            player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
        }
    }
}
