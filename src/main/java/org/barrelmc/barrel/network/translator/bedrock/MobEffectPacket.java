package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.PotionConverter;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;

public class MobEffectPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket) pk;

        // A java client is only told about the effects of its own player
        Effect effect = PotionConverter.bedrockToJavaEffect(packet.getEffectId());
        if (packet.getRuntimeEntityId() != player.getRuntimeEntityId() || effect == null) {
            return;
        }

        int entityId = (int) packet.getRuntimeEntityId();
        switch (packet.getEvent()) {
            case ADD:
            case MODIFY:
                player.getEffects().put(effect, packet.getAmplifier());
                player.getJavaSession().send(new ClientboundUpdateMobEffectPacket(entityId, effect, packet.getAmplifier(), packet.getDuration(), packet.isAmbient(), packet.isParticles(), true, false));
                break;
            case REMOVE:
                player.getEffects().remove(effect);
                player.getJavaSession().send(new ClientboundRemoveMobEffectPacket(entityId, effect));
                break;
            default:
                return;
        }

        if (effect == Effect.SPEED || effect == Effect.SLOWNESS) {
            TranslatorUtils.sendMovementSpeed(player);
        }
    }
}
