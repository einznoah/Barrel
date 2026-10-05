package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;

public class SetCarriedItemPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundSetCarriedItemPacket packet = (ServerboundSetCarriedItemPacket) pk;

        if (packet.getSlot() >= 0 && packet.getSlot() < 9) {
            player.getInventory().setHeldSlot(packet.getSlot());
        }
    }
}
