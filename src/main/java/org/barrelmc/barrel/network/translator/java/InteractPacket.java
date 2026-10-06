package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;

public class InteractPacket implements JavaPacketTranslator {

    private static final int ACTION_INTERACT = 0;

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundInteractPacket packet = (ServerboundInteractPacket) pk;

        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        InventoryTransactionPacket inventoryTransactionPacket = new InventoryTransactionPacket();
        inventoryTransactionPacket.setTransactionType(InventoryTransactionType.ITEM_USE_ON_ENTITY);
        inventoryTransactionPacket.setActionType(ACTION_INTERACT);
        inventoryTransactionPacket.setRuntimeEntityId(packet.getEntityId());
        inventoryTransactionPacket.setHotbarSlot(player.getInventory().getHeldSlot());
        inventoryTransactionPacket.setItemInHand(player.getInventory().getHeldItemSlot().get());
        inventoryTransactionPacket.setPlayerPosition(player.getVector3f());
        inventoryTransactionPacket.setClickPosition(packet.getLocation() == null ? Vector3f.ZERO : packet.getLocation().toFloat());

        player.getBedrockSession().sendPacket(inventoryTransactionPacket);
    }
}
