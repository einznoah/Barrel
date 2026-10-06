package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;

public class SetEntityMotionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket) pk;
        Vector3f motion = packet.getMotion();

        // The motion of the player itself is the knockback it takes
        if (packet.getRuntimeEntityId() == player.getRuntimeEntityId() || player.getEntities().containsKey(packet.getRuntimeEntityId())) {
            player.getJavaSession().send(new ClientboundSetEntityMotionPacket((int) packet.getRuntimeEntityId(), Vector3d.from(motion.getX(), motion.getY(), motion.getZ())));
        }
    }
}
