package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.ChestValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.ChestValueType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

public class BlockEventPacket implements BedrockPacketTranslator {

    // Id of minecraft:chest in the java block registry
    private static final int JAVA_CHEST_ID = 245;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket) pk;

        if (packet.getEventType() == 1) {
            Vector3i pos = packet.getBlockPosition();
            int viewers = packet.getEventData() == 2 ? 1 : 0;
            player.getJavaSession().send(new ClientboundBlockEventPacket(pos, 1, viewers, ChestValueType.VIEWING_PLAYER_COUNT, new ChestValue(viewers), JAVA_CHEST_ID));
        }
    }
}
