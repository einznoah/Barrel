package org.barrelmc.barrel.player;

import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;

// Rain and thunder. A server of mojang tells how they are when the player joins, and later when they start and
// stop and how strong they are to be; a bedrock client fades them in and out itself. A java client is told when it
// starts and stops raining and how strong rain and thunder are at each tick, as a java server does, which fades
// them by a hundredth a tick. This belongs to the thread that translates the packets
public class Weather {

    private static final float FADE = 0.01F;

    private final Player player;
    private boolean raining;
    // How strong they are for the java client, and are to be
    private float rain;
    private float thunder;
    private float rainTarget;
    private float thunderTarget;
    private boolean started;

    public Weather(Player player) {
        this.player = player;
    }

    // As it is when the player joins
    public void set(float rain, float thunder) {
        this.rain = this.rainTarget = Math.clamp(rain, 0F, 1F);
        this.thunder = this.thunderTarget = Math.clamp(thunder, 0F, 1F);
        this.raining = this.rain > 0;
        this.resend();
    }

    // A java client makes a new world when the player comes into another dimension or back to life, which has none
    public void resend() {
        if (this.rain > 0) {
            this.player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.START_RAINING, null));
            this.sendStrength();
        }
    }

    public void rain(float strength) {
        this.rainTarget = Math.clamp(strength, 0F, 1F);
        if (this.raining != this.rainTarget > 0) {
            this.raining = this.rainTarget > 0;
            this.player.getJavaSession().send(new ClientboundGameEventPacket(this.raining ? GameEvent.START_RAINING : GameEvent.STOP_RAINING, null));
            // Starting and stopping make it as strong as rain is when it starts or stops, it is as strong as it is
            this.sendStrength();
        }
        this.start();
    }

    public void thunder(float strength) {
        this.thunderTarget = Math.clamp(strength, 0F, 1F);
        this.start();
    }

    private void start() {
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tick);
        }
    }

    private void tick() {
        if (this.rain != this.rainTarget) {
            this.rain = this.rain < this.rainTarget ? Math.min(this.rainTarget, this.rain + FADE) : Math.max(this.rainTarget, this.rain - FADE);
            this.player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.RAIN_LEVEL_CHANGE, new RainStrengthValue(this.rain)));
        }
        if (this.thunder != this.thunderTarget) {
            this.thunder = this.thunder < this.thunderTarget ? Math.min(this.thunderTarget, this.thunder + FADE) : Math.max(this.thunderTarget, this.thunder - FADE);
            this.player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.THUNDER_LEVEL_CHANGE, new ThunderStrengthValue(this.thunder)));
        }
    }

    private void sendStrength() {
        this.player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.RAIN_LEVEL_CHANGE, new RainStrengthValue(this.rain)));
        this.player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.THUNDER_LEVEL_CHANGE, new ThunderStrengthValue(this.thunder)));
    }
}
