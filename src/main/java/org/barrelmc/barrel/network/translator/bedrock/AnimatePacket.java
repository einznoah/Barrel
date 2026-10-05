package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.SwingAnimation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSwingAnimationPacket;

public class AnimatePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AnimatePacket packet = (org.cloudburstmc.protocol.bedrock.packet.AnimatePacket) pk;

        switch (packet.getAction()) {
            case SWING_ARM: {
                player.getJavaSession().send(new ClientboundSwingAnimationPacket((int) packet.getRuntimeEntityId(), Hand.MAIN_HAND, new SwingAnimation(SwingAnimation.Type.WHACK, 6)));
                break;
            }
            case WAKE_UP: {
                player.getJavaSession().send(new ClientboundAnimatePacket((int) packet.getRuntimeEntityId(), Animation.WAKE_UP));
                break;
            }
            case CRITICAL_HIT: {
                player.getJavaSession().send(new ClientboundAnimatePacket((int) packet.getRuntimeEntityId(), Animation.CRITICAL_HIT));
                break;
            }
            case MAGIC_CRITICAL_HIT: {
                player.getJavaSession().send(new ClientboundAnimatePacket((int) packet.getRuntimeEntityId(), Animation.MAGIC_CRITICAL_HIT));
                break;
            }
        }
    }
}
