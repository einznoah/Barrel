package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.network.converter.SoundConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStopSoundPacket;

// A sound that is still heard is to stop: one sound by its name, or all of them
public class StopSoundPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket packet = (org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket) pk;

        if (packet.isStoppingAllSound()) {
            player.getJavaSession().send(new ClientboundStopSoundPacket(null, null));
            return;
        }
        BuiltinSound sound = SoundConverter.getJavaSound(packet.getSoundName());
        if (sound != null) {
            player.getJavaSession().send(new ClientboundStopSoundPacket(null, Key.key(sound.getName())));
        }
    }
}
