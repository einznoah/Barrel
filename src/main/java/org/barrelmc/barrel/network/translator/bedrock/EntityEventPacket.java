package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundHurtAnimationPacket;

public class EntityEventPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket) pk;

        boolean knownEntity = player.getEntities().containsKey(packet.getRuntimeEntityId());
        switch (packet.getType()) {
            case HURT: {
                if (knownEntity || packet.getRuntimeEntityId() == player.getRuntimeEntityId()) {
                    player.getJavaSession().send(new ClientboundHurtAnimationPacket((int) packet.getRuntimeEntityId(), 0));
                }
                break;
            }
            case DEATH: {
                // TODO: The health of the player, it does not see itself die
                if (knownEntity) {
                    player.getJavaSession().send(new ClientboundEntityEventPacket((int) packet.getRuntimeEntityId(), EntityEvent.LIVING_DEATH));
                }
                break;
            }
        }
    }
}
