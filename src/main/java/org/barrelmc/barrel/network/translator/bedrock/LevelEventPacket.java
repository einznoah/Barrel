package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.BlockBreakStage;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.BreakBlockEventData;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.BreakPotionEventData;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LevelEventPacket implements BedrockPacketTranslator {

    private static final float WEATHER_FULL = 65535;
    // The sounds of what is done with the item of an item frame, and of a glow item frame
    private static final Map<LevelEvent, BuiltinSound[]> FRAME_SOUNDS = Map.of(
            LevelEvent.SOUND_ITEMFRAME_ITEM_ADD, new BuiltinSound[]{BuiltinSound.ENTITY_ITEM_FRAME_ADD_ITEM, BuiltinSound.ENTITY_GLOW_ITEM_FRAME_ADD_ITEM},
            LevelEvent.SOUND_ITEMFRAME_ITEM_REMOVE, new BuiltinSound[]{BuiltinSound.ENTITY_ITEM_FRAME_REMOVE_ITEM, BuiltinSound.ENTITY_GLOW_ITEM_FRAME_REMOVE_ITEM},
            LevelEvent.SOUND_ITEMFRAME_ITEM_ROTATE, new BuiltinSound[]{BuiltinSound.ENTITY_ITEM_FRAME_ROTATE_ITEM, BuiltinSound.ENTITY_GLOW_ITEM_FRAME_ROTATE_ITEM});

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket) pk;

        if (packet.getType() == LevelEvent.BLOCK_START_BREAK) {
            Vector3f pos = packet.getPosition();
            player.getJavaSession().send(new ClientboundBlockDestructionPacket(0, pos.toInt(), BlockBreakStage.STAGE_1));
            // The data is how fast the block breaks, an item frame that is hit is broken in that time
            player.getItemFrames().breakStarted(pos.toInt(), packet.getData());
        } else if (packet.getType() == LevelEvent.BLOCK_STOP_BREAK) {
            Vector3f pos = packet.getPosition();
            player.getJavaSession().send(new ClientboundBlockDestructionPacket(0, pos.toInt(), BlockBreakStage.RESET));
        } else if (packet.getType() == LevelEvent.PARTICLE_DESTROY_BLOCK) {
            // An item frame that breaks is an entity for a java client, which only makes its sound
            BlockConverter.BedrockFrame frame = BlockConverter.getBedrockFrame(packet.getData(), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
            if (frame != null) {
                Vector3f pos = packet.getPosition();
                player.getJavaSession().send(new ClientboundSoundPacket(frame.glow() ? BuiltinSound.ENTITY_GLOW_ITEM_FRAME_BREAK : BuiltinSound.ENTITY_ITEM_FRAME_BREAK, SoundCategory.BLOCK,
                        pos.getX(), pos.getY(), pos.getZ(), 1, 1, ThreadLocalRandom.current().nextLong()));
                return;
            }
            // A block breaks: a java client shows the pieces and makes the sound when it is told which block it was
            int javaBlock = BlockConverter.bedrockRuntimeToJavaStateId(packet.getData(), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
            // Not for a block the player broke itself, of that its client shows and sounds all of it
            if (player.isOwnAction(packet.getPosition())) {
                return;
            }
            player.getJavaSession().send(new ClientboundLevelEventPacket(LevelEventType.PARTICLES_DESTROY_BLOCK, packet.getPosition().toInt(), new BreakBlockEventData(javaBlock)));
        } else if (packet.getType() == LevelEvent.PARTICLE_POTION_SPLASH) {
            // The data is the color of the potion that broke
            player.getJavaSession().send(new ClientboundLevelEventPacket(LevelEventType.PARTICLES_SPELL_POTION_SPLASH, packet.getPosition().toInt(), new BreakPotionEventData(packet.getData() & 0xFFFFFF)));
        } else if (FRAME_SOUNDS.containsKey(packet.getType())) {
            // What is done with the item of a frame, at the block of the frame
            Vector3f pos = packet.getPosition();
            BlockConverter.BedrockFrame frame = BlockConverter.getBedrockFrame(player.getBedrockBlocks().getBlock(pos.toInt()), player.getStartGamePacketCache().isBlockNetworkIdsHashed());
            BuiltinSound[] sounds = FRAME_SOUNDS.get(packet.getType());
            player.getJavaSession().send(new ClientboundSoundPacket(sounds[frame != null && frame.glow() ? 1 : 0], SoundCategory.BLOCK, pos.getFloorX() + 0.5, pos.getFloorY() + 0.5, pos.getFloorZ() + 0.5,
                    1, 1, ThreadLocalRandom.current().nextLong()));
        } else if (packet.getType() == LevelEvent.START_RAINING) {
            // The data is how strong it is to be, out of 65535
            player.getWeather().rain(packet.getData() / WEATHER_FULL);
        } else if (packet.getType() == LevelEvent.STOP_RAINING) {
            player.getWeather().rain(0);
        } else if (packet.getType() == LevelEvent.START_THUNDERSTORM) {
            player.getWeather().thunder(packet.getData() / WEATHER_FULL);
        } else if (packet.getType() == LevelEvent.STOP_THUNDERSTORM) {
            player.getWeather().thunder(0);
        }
    }
}
