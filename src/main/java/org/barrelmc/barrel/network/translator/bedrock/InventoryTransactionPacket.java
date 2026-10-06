package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// A server of mojang tells this way what it changed in the inventory of the player itself, an item that was
// picked up for one
public class InventoryTransactionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket) pk;

        // What the client asked for itself is answered otherwise
        if (packet.getLegacyRequestId() != 0) {
            return;
        }

        for (InventoryActionData action : packet.getActions()) {
            if (action.getSource().getType() != InventorySource.Type.CONTAINER || action.getToItem() == null) {
                continue;
            }

            ItemData item = action.getToItem();
            if (action.getStackNetworkId() != 0) {
                // The server knows the stack by this from now on, it is not told with the item here
                item = item.toBuilder().usingNetId(true).netId(action.getStackNetworkId()).build();
            }
            player.getInventory().setSlot(action.getSource().getContainerId(), action.getSlot(), item);
        }
    }
}
