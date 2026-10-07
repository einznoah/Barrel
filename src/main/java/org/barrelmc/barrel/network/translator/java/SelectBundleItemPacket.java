package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSelectBundleItemPacket;

// The player points at one of the items in a bundle, which is the one a click takes out
public class SelectBundleItemPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundSelectBundleItemPacket packet = (ServerboundSelectBundleItemPacket) pk;

        player.getInventory().selectBundleItem(packet.getSlotId(), packet.getSelectedItemIndex());
    }
}
