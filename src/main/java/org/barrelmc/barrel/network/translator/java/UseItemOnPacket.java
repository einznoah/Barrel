package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

public class UseItemOnPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundUseItemOnPacket packet = (ServerboundUseItemOnPacket) pk;

        // The client keeps the blocks it expects to have changed until the server acknowledged its action
        player.getJavaSession().send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        player.setOwnAction(packet.getPosition());
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        player.getInventory().useItemOn(packet.getPosition(), packet.getFace(), packet.isInsideBlock(), Vector3f.from(packet.getCursorX(), packet.getCursorY(), packet.getCursorZ()));
    }
}
