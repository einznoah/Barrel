package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

public class SwingPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        AnimatePacket animatePacket = new AnimatePacket();
        animatePacket.setAction(AnimatePacket.Action.SWING_ARM);
        animatePacket.setSwingSource(AnimatePacket.SwingSource.NONE);
        animatePacket.setRuntimeEntityId(player.getRuntimeEntityId());

        player.getBedrockSession().sendPacket(animatePacket);
    }
}
