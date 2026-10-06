package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;

public class UpdateAttributesPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket) pk;

        if (packet.getRuntimeEntityId() != player.getRuntimeEntityId()) {
            return;
        }

        // TODO: health and hunger
        boolean experienceChanged = false;
        for (AttributeData attribute : packet.getAttributes()) {
            if (attribute.getName().equals("minecraft:player.level")) {
                player.setExperienceLevel((int) attribute.getValue());
                experienceChanged = true;
            } else if (attribute.getName().equals("minecraft:player.experience")) {
                player.setExperienceProgress(attribute.getValue());
                experienceChanged = true;
            }
        }

        if (experienceChanged) {
            player.getJavaSession().send(new ClientboundSetExperiencePacket(player.getExperienceProgress(), player.getExperienceLevel(), 0));
        }
    }
}
