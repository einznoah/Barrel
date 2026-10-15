package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTakeItemEntityPacket;

public class TakeItemEntityPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.TakeItemEntityPacket) pk;

        player.getEntities().remove(packet.getItemRuntimeEntityId());
        // The java client makes the sound and shows the item flying to the player for an item it still has, it is
        // told that the item is gone after it
        player.getJavaSession().send(new ClientboundTakeItemEntityPacket((int) packet.getItemRuntimeEntityId(), (int) packet.getRuntimeEntityId(), 1));

        int[] entityIds = new int[1];
        entityIds[0] = (int) packet.getItemRuntimeEntityId();
        player.getJavaSession().send(new ClientboundRemoveEntitiesPacket(entityIds));
    }
}
