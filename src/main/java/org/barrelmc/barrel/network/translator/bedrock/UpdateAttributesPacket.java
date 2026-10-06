package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;

public class UpdateAttributesPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket) pk;

        if (packet.getRuntimeEntityId() != player.getRuntimeEntityId()) {
            return;
        }

        boolean experienceChanged = false;
        boolean healthChanged = false;
        for (AttributeData attribute : packet.getAttributes()) {
            if (attribute.getName().equals("minecraft:health")) {
                player.setHealth(attribute.getValue());
                healthChanged = true;
            } else if (attribute.getName().equals("minecraft:player.hunger")) {
                player.setFood((int) attribute.getValue());
                healthChanged = true;
            } else if (attribute.getName().equals("minecraft:player.saturation")) {
                player.setSaturation(attribute.getValue());
                healthChanged = true;
            } else if (attribute.getName().equals("minecraft:player.level")) {
                player.setExperienceLevel((int) attribute.getValue());
                experienceChanged = true;
            } else if (attribute.getName().equals("minecraft:player.experience")) {
                player.setExperienceProgress(attribute.getValue());
                experienceChanged = true;
            }
        }

        if (healthChanged) {
            // Without health left the java client shows that the player died, and asks to come back to life
            player.getJavaSession().send(new ClientboundSetHealthPacket(player.getHealth(), player.getFood(), player.getSaturation()));
        }
        if (experienceChanged) {
            player.getJavaSession().send(new ClientboundSetExperiencePacket(player.getExperienceProgress(), player.getExperienceLevel(), 0));
        }
    }
}
