package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Inventory;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.SpreadItemAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

public class ContainerClickPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundContainerClickPacket packet = (ServerboundContainerClickPacket) pk;
        Inventory inventory = player.getInventory();
        Inventory.Slot slot = inventory.getJavaSlot(packet.getContainerId(), packet.getSlot());

        switch (packet.getAction()) {
            case CLICK_ITEM:
                if (packet.getSlot() == ServerboundContainerClickPacket.CLICK_OUTSIDE_NOT_HOLDING_SLOT) {
                    // A click next to the window drops what is on the cursor
                    inventory.dropItem(inventory.getCursorSlot(), packet.getParam() == ClickItemAction.LEFT_CLICK);
                } else if (slot != null) {
                    if (packet.getParam() == ClickItemAction.LEFT_CLICK) {
                        inventory.leftClick(slot);
                    } else {
                        inventory.rightClick(slot);
                    }
                }
                break;
            case SHIFT_CLICK_ITEM:
                inventory.shiftClick(packet.getContainerId(), packet.getSlot());
                break;
            case MOVE_TO_HOTBAR_SLOT:
                if (slot != null) {
                    MoveToHotbarAction hotbarAction = (MoveToHotbarAction) packet.getParam();
                    inventory.swapSlots(slot, hotbarAction == MoveToHotbarAction.OFF_HAND ? inventory.getOffhandSlot() : inventory.getHotbarSlot(hotbarAction.ordinal()));
                }
                break;
            case DROP_ITEM:
                if (slot != null) {
                    inventory.dropItem(slot, packet.getParam() == DropItemAction.DROP_SELECTED_STACK);
                }
                break;
            case FILL_STACK:
                inventory.doubleClick(packet.getContainerId());
                break;
            case SPREAD_ITEM:
                switch ((SpreadItemAction) packet.getParam()) {
                    case LEFT_MOUSE_BEGIN_DRAG:
                    case RIGHT_MOUSE_BEGIN_DRAG:
                        inventory.startDrag();
                        break;
                    case LEFT_MOUSE_ADD_SLOT:
                    case RIGHT_MOUSE_ADD_SLOT:
                        if (slot != null) {
                            inventory.addDragSlot(slot);
                        }
                        break;
                    case LEFT_MOUSE_END_DRAG:
                    case RIGHT_MOUSE_END_DRAG:
                        inventory.endDrag(packet.getParam() == SpreadItemAction.LEFT_MOUSE_END_DRAG);
                        break;
                    default:
                        // The middle button only does something in the creative inventory
                        break;
                }
                // The client is still dragging, it only knows what the slots end up with once it let go
                if (packet.getParam() != SpreadItemAction.LEFT_MOUSE_END_DRAG && packet.getParam() != SpreadItemAction.RIGHT_MOUSE_END_DRAG) {
                    return;
                }
                break;
            default:
                // The middle button, it only does something in the creative inventory
                break;
        }

        // The client already changed its inventory to what it expects of the click, it is shown what the server was asked for instead
        inventory.sendContents();
    }
}
