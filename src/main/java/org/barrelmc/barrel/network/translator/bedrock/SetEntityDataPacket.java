package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class SetEntityDataPacket implements BedrockPacketTranslator {

    // How high a bed is
    private static final double BED_HEIGHT = 0.5625;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket packet = (org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket) pk;

        if (packet.getRuntimeEntityId() == player.getRuntimeEntityId()) {
            if (packet.getMetadata().getFlags() != null) {
                player.setImmobile(packet.getMetadata().getFlag(EntityFlag.NO_AI));
            }
            boolean slept = player.getSelf().isSleeping();
            TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), player.getSelf(), packet.getMetadata());
            if (player.getSelf().isSleeping() && !slept) {
                // A player that goes to sleep is in its bed for the server, a java server puts its client there
                Vector3i bed = player.getSelf().getBedPosition();
                player.setPosition(bed.getX() + 0.5, bed.getY() + BED_HEIGHT, bed.getZ() + 0.5);
                player.getInput().teleportJava();
            }
        } else {
            Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
            if (entity != null) {
                TranslatorUtils.sendEntityData(player, packet.getRuntimeEntityId(), entity, packet.getMetadata());
            }
        }
    }
}
