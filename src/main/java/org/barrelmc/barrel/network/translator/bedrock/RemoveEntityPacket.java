package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;

public class RemoveEntityPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket packet = (org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket) pk;

        // Not every server has two ids for an entity, and the client may not have been told about this one
        Long runtimeEntityId = player.getEntityRuntimeIds().remove(packet.getUniqueEntityId());
        if (runtimeEntityId == null) {
            runtimeEntityId = packet.getUniqueEntityId();
        }
        player.getEntities().remove(runtimeEntityId);
        player.getRiding().remove(runtimeEntityId);

        int[] entityIds = new int[1];
        entityIds[0] = (int) (long) runtimeEntityId;
        player.getJavaSession().send(new ClientboundRemoveEntitiesPacket(entityIds));
    }
}
