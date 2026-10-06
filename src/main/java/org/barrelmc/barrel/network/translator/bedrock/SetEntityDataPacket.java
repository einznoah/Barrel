package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class SetEntityDataPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket) pk;

        if (packet.getRuntimeEntityId() == player.getRuntimeEntityId()) {
            if (packet.getMetadata().getFlags() != null) {
                player.setImmobile(packet.getMetadata().getFlag(EntityFlag.NO_AI));
            }
            TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), player.getSelf(), packet.getMetadata());
        } else {
            Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
            if (entity != null) {
                TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), entity, packet.getMetadata());
            }
        }
    }
}
