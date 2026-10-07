package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.network.converter.SoundConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// Something is heard: a chest opens, a cow moos, somebody walks
public class LevelSoundEventPacket implements BedrockPacketTranslator {

    // What a java client makes heard itself when its own player does it: the server tells these with the player
    private static final Set<SoundEvent> OWN = EnumSet.of(SoundEvent.STEP, SoundEvent.HEAVY_STEP, SoundEvent.JUMP, SoundEvent.FALL, SoundEvent.LAND, SoundEvent.FALL_BIG,
            SoundEvent.FALL_SMALL, SoundEvent.SWIM, SoundEvent.SPLASH);
    // And what it makes heard itself when its own player does it to a block: the server tells these with the block
    // only, they are told apart by where they come from right after the player did something there
    private static final Set<SoundEvent> OWN_AT_BLOCK = EnumSet.of(SoundEvent.HIT, SoundEvent.PLACE, SoundEvent.ITEM_USE_ON, SoundEvent.BREAK, SoundEvent.BREAK_BLOCK, SoundEvent.DOOR_OPEN,
            SoundEvent.DOOR_CLOSE, SoundEvent.TRAPDOOR_OPEN, SoundEvent.TRAPDOOR_CLOSE, SoundEvent.FENCE_GATE_OPEN, SoundEvent.FENCE_GATE_CLOSE, SoundEvent.BUTTON_CLICK_ON);
    // What is told with a block as the number that goes with the event
    private static final Set<SoundEvent> WITH_BLOCK = EnumSet.of(SoundEvent.STEP, SoundEvent.HEAVY_STEP, SoundEvent.JUMP, SoundEvent.FALL, SoundEvent.LAND, SoundEvent.HIT, SoundEvent.PLACE,
            SoundEvent.ITEM_USE_ON, SoundEvent.BREAK, SoundEvent.BREAK_BLOCK, SoundEvent.DOOR_OPEN, SoundEvent.DOOR_CLOSE, SoundEvent.TRAPDOOR_OPEN, SoundEvent.TRAPDOOR_CLOSE,
            SoundEvent.FENCE_GATE_OPEN, SoundEvent.FENCE_GATE_CLOSE, SoundEvent.BUTTON_CLICK_ON, SoundEvent.BUTTON_CLICK_OFF, SoundEvent.PRESSURE_PLATE_CLICK_ON,
            SoundEvent.PRESSURE_PLATE_CLICK_OFF);

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket) pk;
        SoundEvent event = packet.getSound();
        if (event == null || (OWN.contains(event) && packet.getEntityUniqueId() == player.getUniqueEntityId()) || (OWN_AT_BLOCK.contains(event) && player.isOwnAction(packet.getPosition()))) {
            return;
        }

        String block = WITH_BLOCK.contains(event) ? BlockConverter.getBedrockName(packet.getExtraData(), player.getStartGamePacketCache().isBlockNetworkIdsHashed()) : null;
        BuiltinSound sound = SoundConverter.getJavaSound(event, packet.getExtraData(), packet.getIdentifier(), block);
        if (sound == null) {
            return;
        }
        Vector3f position = packet.getPosition();
        player.getJavaSession().send(new ClientboundSoundPacket(sound, SoundConverter.getCategory(sound), position.getX(), position.getY(), position.getZ(), 1,
                SoundConverter.getPitch(event, packet.getExtraData(), packet.isBabySound()), ThreadLocalRandom.current().nextLong()));
    }
}
