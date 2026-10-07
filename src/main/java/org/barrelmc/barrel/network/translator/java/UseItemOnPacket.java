package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

public class UseItemOnPacket implements JavaPacketTranslator {

    private static final int ACTION_CLICK_BLOCK = 0;

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundUseItemOnPacket packet = (ServerboundUseItemOnPacket) pk;

        // The client keeps the blocks it expects to have changed until the server acknowledged its action
        player.getJavaSession().send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        player.setOwnAction(packet.getPosition());
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        // A bedrock client says that it starts to use what it holds on a block, and after it that it stopped
        Vector3i neighbor = packet.getPosition();
        if (!packet.isInsideBlock()) {
            switch (packet.getFace()) {
                case DOWN -> neighbor = neighbor.down();
                case UP -> neighbor = neighbor.up();
                case NORTH -> neighbor = neighbor.north();
                case SOUTH -> neighbor = neighbor.south();
                case WEST -> neighbor = neighbor.west();
                case EAST -> neighbor = neighbor.east();
            }
        }
        PlayerActionPacket startPacket = new PlayerActionPacket();
        startPacket.setRuntimeEntityId(player.getRuntimeEntityId());
        startPacket.setAction(PlayerActionType.START_ITEM_USE_ON);
        startPacket.setBlockPosition(packet.getPosition());
        startPacket.setResultPosition(neighbor);
        startPacket.setFace(packet.getFace().ordinal());
        player.getBedrockSession().sendPacket(startPacket);

        InventoryTransactionPacket inventoryTransactionPacket = new InventoryTransactionPacket();
        inventoryTransactionPacket.setTransactionType(InventoryTransactionType.ITEM_USE);
        inventoryTransactionPacket.setActionType(ACTION_CLICK_BLOCK);
        inventoryTransactionPacket.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
        inventoryTransactionPacket.setBlockPosition(packet.getPosition());
        inventoryTransactionPacket.setBlockFace(packet.getFace().ordinal());
        inventoryTransactionPacket.setHotbarSlot(player.getInventory().getHeldSlot());
        inventoryTransactionPacket.setHand(HandSlot.MAINHAND);
        ItemData heldItem = player.getInventory().getHeldItemSlot().get();
        // A block that is put down is one less in the hand, the server is told what the client expects
        ItemData leftItem = heldItem;
        if (heldItem.getBlockDefinition() != null && player.getGameMode() != GameType.CREATIVE) {
            leftItem = heldItem.getCount() > 1 ? heldItem.toBuilder().count(heldItem.getCount() - 1).build() : ItemData.AIR;
        }
        inventoryTransactionPacket.getActions().add(new InventoryActionData(InventorySource.fromContainerWindowId(ContainerId.INVENTORY), player.getInventory().getHeldSlot(), heldItem, leftItem, 0));
        inventoryTransactionPacket.setItemInHand(heldItem);
        inventoryTransactionPacket.setPlayerPosition(player.getVector3f());
        inventoryTransactionPacket.setClickPosition(Vector3f.from(packet.getCursorX(), packet.getCursorY(), packet.getCursorZ()));
        // A server of mojang does not take a click on a block it has another block at
        int clickedBlock = player.getBedrockBlocks().getBlock(packet.getPosition());
        inventoryTransactionPacket.setBlockDefinition(() -> clickedBlock);
        inventoryTransactionPacket.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);

        player.getBedrockSession().sendPacket(inventoryTransactionPacket);

        PlayerActionPacket stopPacket = new PlayerActionPacket();
        stopPacket.setRuntimeEntityId(player.getRuntimeEntityId());
        stopPacket.setAction(PlayerActionType.STOP_ITEM_USE_ON);
        stopPacket.setBlockPosition(packet.getPosition());
        stopPacket.setResultPosition(Vector3i.ZERO);
        stopPacket.setFace(0);
        player.getBedrockSession().sendPacket(stopPacket);
    }
}
