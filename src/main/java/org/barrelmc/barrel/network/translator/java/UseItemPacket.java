package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

public class UseItemPacket implements JavaPacketTranslator {

    private static final int ACTION_CLICK_AIR = 1;

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundUseItemPacket packet = (ServerboundUseItemPacket) pk;

        player.getJavaSession().send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        InventoryTransactionPacket inventoryTransactionPacket = new InventoryTransactionPacket();
        inventoryTransactionPacket.setTransactionType(InventoryTransactionType.ITEM_USE);
        inventoryTransactionPacket.setActionType(ACTION_CLICK_AIR);
        inventoryTransactionPacket.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
        inventoryTransactionPacket.setBlockPosition(Vector3i.ZERO);
        inventoryTransactionPacket.setBlockFace(255);
        inventoryTransactionPacket.setHotbarSlot(player.getInventory().getHeldSlot());
        inventoryTransactionPacket.setHand(HandSlot.MAINHAND);
        inventoryTransactionPacket.setItemInHand(player.getInventory().getHeldItemSlot().get());
        inventoryTransactionPacket.setPlayerPosition(player.getVector3f());
        inventoryTransactionPacket.setClickPosition(Vector3f.ZERO);
        inventoryTransactionPacket.setBlockDefinition(() -> 0);
        inventoryTransactionPacket.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);

        player.getBedrockSession().sendPacket(inventoryTransactionPacket);
    }
}
