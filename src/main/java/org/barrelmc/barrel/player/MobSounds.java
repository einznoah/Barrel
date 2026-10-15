package org.barrelmc.barrel.player;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.SoundConverter;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.concurrent.ThreadLocalRandom;

// A server of mojang does not tell the sounds a mob makes now and then, the moo of a cow or the groan of a zombie:
// a bedrock client makes them heard itself. A java client is told them by its server, so they are made here as a
// java server makes them: each tick a mob makes its sound with a chance that grows the longer it was quiet, once it
// waited the ticks it waits at least. Only the mobs close enough to be heard are told of.
// This belongs to the thread that translates the packets
public class MobSounds {

    // How far a sound as loud as most is heard
    private static final double HEARD_WITHIN = 16;

    private final Player player;
    private boolean started;

    public MobSounds(Player player) {
        this.player = player;
    }

    // The player has spawned
    public void start() {
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tick);
        }
    }

    private void tick() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Entity entity : this.player.getEntities().values()) {
            SoundConverter.MobSound sound = SoundConverter.getMobSound(entity.getType());
            if (sound == null || entity.isDead() || entity.isStandIn()) {
                continue;
            }
            // A mob that comes into view waits before it makes its sound, so that not all of them do at once
            Integer time = entity.getAmbientSoundTime();
            if (time == null || random.nextInt(1000) >= time) {
                entity.setAmbientSoundTime(time == null ? -random.nextInt(sound.interval()) - sound.interval() : time + 1);
                continue;
            }
            entity.setAmbientSoundTime(-sound.interval());

            double reach = HEARD_WITHIN * Math.max(1, sound.volume());
            double dx = entity.x - this.player.x, dy = entity.y - this.player.y, dz = entity.z - this.player.z;
            if (entity.isSilent() || dx * dx + dy * dy + dz * dz > reach * reach) {
                continue;
            }
            // Young ones sound higher, and every mob a little higher or lower each time
            float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + (entity.isBaby() ? 1.5F : 1);
            this.player.getJavaSession().send(new ClientboundSoundPacket(sound.sound(), SoundConverter.getCategory(sound.sound()), entity.x, entity.y, entity.z,
                    sound.volume(), pitch, random.nextLong()));
        }
    }
}
