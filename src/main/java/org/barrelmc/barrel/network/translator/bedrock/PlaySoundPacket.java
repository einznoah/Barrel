package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.SoundConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEventType;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.RecordEventData;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.concurrent.ThreadLocalRandom;

// A sound is told by its name: what a command or an add-on plays, and the record of a jukebox
public class PlaySoundPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket) pk;
        Vector3f position = packet.getPosition();

        // A java client plays the record of a jukebox as the song of the block the jukebox is, and goes on with it
        // until it is told that the jukebox stopped
        String song = SoundConverter.getJukeboxSong(packet.getSound());
        Integer songId = song == null ? null : ProxyServer.getInstance().getJukeboxSong(song);
        if (songId != null) {
            Vector3i jukebox = position.toInt();
            player.getJukeboxes().add(jukebox);
            player.getJavaSession().send(new ClientboundLevelEventPacket(LevelEventType.SOUND_PLAY_JUKEBOX_SONG, jukebox, new RecordEventData(songId)));
            return;
        }

        BuiltinSound sound = SoundConverter.getJavaSound(packet.getSound());
        if (sound != null) {
            player.getJavaSession().send(new ClientboundSoundPacket(sound, SoundConverter.getCategory(sound), position.getX(), position.getY(), position.getZ(), packet.getVolume(),
                    packet.getPitch(), ThreadLocalRandom.current().nextLong()));
        }
    }
}
