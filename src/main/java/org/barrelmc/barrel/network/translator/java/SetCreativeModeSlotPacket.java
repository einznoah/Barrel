package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Inventory;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;

public class SetCreativeModeSlotPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundSetCreativeModeSlotPacket packet = (ServerboundSetCreativeModeSlotPacket) pk;

        // TODO: slot -1, an item thrown out of the creative inventory
        Inventory.Slot slot = player.getInventory().getJavaSlot(0, packet.getSlot());
        if (slot == null) {
            return;
        }

        if (player.getGameMode() == GameType.CREATIVE) {
            player.getInventory().setCreativeItem(slot, packet.getClickedItem());
        } else {
            player.getInventory().sendSlot(slot);
        }
    }
}
