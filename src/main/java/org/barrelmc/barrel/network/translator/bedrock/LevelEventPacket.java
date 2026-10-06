package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.BlockBreakStage;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.BreakPotionEventData;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;

public class LevelEventPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket) pk;

        if (packet.getType() == LevelEvent.BLOCK_START_BREAK) {
            Vector3f pos = packet.getPosition();
            player.getJavaSession().send(new ClientboundBlockDestructionPacket(0, pos.toInt(), BlockBreakStage.STAGE_1));
        } else if (packet.getType() == LevelEvent.BLOCK_STOP_BREAK) {
            Vector3f pos = packet.getPosition();
            player.getJavaSession().send(new ClientboundBlockDestructionPacket(0, pos.toInt(), BlockBreakStage.RESET));
        } else if (packet.getType() == LevelEvent.PARTICLE_POTION_SPLASH) {
            // The data is the color of the potion that broke
            player.getJavaSession().send(new ClientboundLevelEventPacket(LevelEventType.PARTICLES_SPELL_POTION_SPLASH, packet.getPosition().toInt(), new BreakPotionEventData(packet.getData() & 0xFFFFFF)));
        }
    }
}
