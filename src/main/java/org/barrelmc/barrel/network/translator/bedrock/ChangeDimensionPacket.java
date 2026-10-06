package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;

public class ChangeDimensionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket) pk;

        player.getBedrockBlocks().clear();

        // The server names the loading screen of a change of dimension, and waits to be told that it opened and closed
        if (packet.getLoadingScreenId() != null) {
            ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
            loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN);
            loadingScreenPacket.setLoadingScreenId(packet.getLoadingScreenId());
            player.getBedrockSession().sendPacket(loadingScreenPacket);
        }

        PlayerActionPacket playerActionPacket = new PlayerActionPacket();
        playerActionPacket.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
        playerActionPacket.setBlockPosition(Vector3i.ZERO);
        playerActionPacket.setResultPosition(Vector3i.ZERO);
        playerActionPacket.setFace(0);
        playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
        player.getBedrockSession().sendPacket(playerActionPacket);

        if (packet.getLoadingScreenId() != null) {
            ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
            loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN);
            loadingScreenPacket.setLoadingScreenId(packet.getLoadingScreenId());
            player.getBedrockSession().sendPacket(loadingScreenPacket);
        }
    }
}
