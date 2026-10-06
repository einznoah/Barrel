package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

public class UseItemPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundUseItemPacket packet = (ServerboundUseItemPacket) pk;

        player.getJavaSession().send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        player.getInventory().useItem();
    }
}
