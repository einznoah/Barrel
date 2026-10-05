/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftCreativeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Inventory {

    // Id the java client is given for the container the bedrock server opened
    public static final int JAVA_CONTAINER_WINDOW = 1;
    // Where the bedrock server puts an item taken from the creative inventory
    private static final int CREATED_OUTPUT_SLOT = 50;

    private final Player player;

    // What the bedrock server holds, with the changes requested from it already applied
    private final ItemData[] items = new ItemData[36];
    private final ItemData[] armor = new ItemData[4];
    private final ItemData[] offhand = new ItemData[1];
    private final ItemData[] cursor = new ItemData[1];
    private final ItemData[] createdOutput = new ItemData[1];
    private ItemData[] container = null;
    private int containerId = ContainerId.NONE;
    private ContainerType containerType = null;

    @Getter
    private int heldSlot = 0;

    private final List<Slot> dragSlots = new ArrayList<>();

    // Keyed like the bedrock items of the ItemConverter
    private final Map<String, CreativeItemData> creativeItems = new HashMap<>();

    private int requestId = -1;
    private final List<ItemStackRequestAction> requestActions = new ArrayList<>();
    private Map<Slot, ItemData> requestChanges = new HashMap<>();
    // The items a request replaced, they are put back if the server refuses it
    private final Map<Integer, Map<Slot, ItemData>> pendingRequests = new HashMap<>();

    public Inventory(Player player) {
        this.player = player;
    }

    public record Slot(ContainerSlotType type, int networkSlot, ItemData[] contents, int index) {

        public ItemData get() {
            return ItemConverter.isEmpty(this.contents[this.index]) ? ItemData.AIR : this.contents[this.index];
        }

        public boolean isEmpty() {
            return ItemConverter.isEmpty(this.contents[this.index]);
        }

        private ItemStackRequestSlotData toNetwork() {
            return new ItemStackRequestSlotData(this.type, this.networkSlot, this.isEmpty() ? 0 : this.get().getNetId(), new FullContainerName(this.type, null));
        }
    }

    private Slot inventorySlot(int slot) {
        return new Slot(slot < 9 ? ContainerSlotType.HOTBAR : ContainerSlotType.INVENTORY, slot, this.items, slot);
    }

    public Slot getHotbarSlot(int slot) {
        return this.inventorySlot(slot);
    }

    public Slot getHeldItemSlot() {
        return this.inventorySlot(this.heldSlot);
    }

    public Slot getOffhandSlot() {
        return new Slot(ContainerSlotType.OFFHAND, 1, this.offhand, 0);
    }

    public Slot getCursorSlot() {
        return new Slot(ContainerSlotType.CURSOR, 0, this.cursor, 0);
    }

    public Slot getJavaSlot(int windowId, int slot) {
        if (windowId == 0) {
            if (slot >= 5 && slot <= 8) {
                return new Slot(ContainerSlotType.ARMOR, slot - 5, this.armor, slot - 5);
            } else if (slot >= 9 && slot <= 35) {
                return this.inventorySlot(slot);
            } else if (slot >= 36 && slot <= 44) {
                return this.inventorySlot(slot - 36);
            } else if (slot == 45) {
                return this.getOffhandSlot();
            }
        } else if (windowId == JAVA_CONTAINER_WINDOW && this.container != null && slot >= 0) {
            // A container is followed by the main inventory and the hotbar
            if (slot < this.container.length) {
                return new Slot(ContainerSlotType.LEVEL_ENTITY, slot, this.container, slot);
            } else if (slot < this.container.length + 27) {
                return this.inventorySlot(slot - this.container.length + 9);
            } else if (slot < this.container.length + 36) {
                return this.inventorySlot(slot - this.container.length - 27);
            }
        }

        // The crafting grid is not translated
        return null;
    }

    private Slot getBedrockSlot(ContainerSlotType type, int slot) {
        switch (type) {
            case HOTBAR:
            case INVENTORY:
            case HOTBAR_AND_INVENTORY:
                return slot >= 0 && slot < this.items.length ? this.inventorySlot(slot) : null;
            case ARMOR:
                return slot >= 0 && slot < this.armor.length ? new Slot(type, slot, this.armor, slot) : null;
            case OFFHAND:
                return this.getOffhandSlot();
            case CURSOR:
                return this.getCursorSlot();
            case LEVEL_ENTITY:
            case BARREL:
            case SHULKER_BOX:
                return this.container != null && slot >= 0 && slot < this.container.length ? new Slot(type, slot, this.container, slot) : null;
            default:
                return null;
        }
    }

    private ItemData[] getContainer(int containerId) {
        if (containerId == ContainerId.INVENTORY) {
            return this.items;
        } else if (containerId == ContainerId.ARMOR) {
            return this.armor;
        } else if (containerId == ContainerId.OFFHAND) {
            return this.offhand;
        } else if (containerId == ContainerId.UI) {
            // The slots that follow the cursor are the crafting grid, which is not translated
            return this.cursor;
        } else if (containerId == this.containerId) {
            return this.container;
        }
        return null;
    }

    public void setContents(int containerId, List<ItemData> contents) {
        if (containerId == this.containerId && this.containerType != null && this.container == null && !this.openJavaContainer(contents.size())) {
            return;
        }

        ItemData[] container = this.getContainer(containerId);
        if (container != null) {
            for (int slot = 0; slot < container.length && slot < contents.size(); slot++) {
                container[slot] = contents.get(slot);
            }
            this.sendContents();
        }
    }

    public void setSlot(int containerId, int slot, ItemData item) {
        ItemData[] container = this.getContainer(containerId);
        if (container != null && slot >= 0 && slot < container.length) {
            container[slot] = item;
            this.sendSlot(container, slot);
        }
    }

    public void sendSlot(Slot slot) {
        this.sendSlot(slot.contents(), slot.index());
    }

    // Unlike sending everything, this leaves the item the client has on its cursor alone
    private void sendSlot(ItemData[] contents, int slot) {
        ItemStack javaItem = ItemConverter.bedrockToJavaItem(contents[slot]);
        if (contents == this.items) {
            this.player.getJavaSession().send(new ClientboundSetPlayerInventoryPacket(slot, javaItem));
        } else if (contents == this.armor) {
            // Java numbers the armor from the feet up
            this.player.getJavaSession().send(new ClientboundSetPlayerInventoryPacket(39 - slot, javaItem));
        } else if (contents == this.offhand) {
            this.player.getJavaSession().send(new ClientboundSetPlayerInventoryPacket(40, javaItem));
        } else if (contents == this.cursor) {
            this.player.getJavaSession().send(new ClientboundSetCursorItemPacket(javaItem));
        } else if (contents == this.container) {
            this.player.getJavaSession().send(new ClientboundContainerSetSlotPacket(JAVA_CONTAINER_WINDOW, 0, slot, javaItem));
        }
    }

    public void sendContents() {
        ItemStack carriedItem = ItemConverter.bedrockToJavaItem(this.cursor[0]);

        ItemStack[] javaItems = new ItemStack[46];
        for (int slot = 5; slot < javaItems.length; slot++) {
            javaItems[slot] = ItemConverter.bedrockToJavaItem(this.getJavaSlot(0, slot).get());
        }
        this.player.getJavaSession().send(new ClientboundContainerSetContentPacket(0, 0, javaItems, carriedItem));

        if (this.container != null) {
            javaItems = new ItemStack[this.container.length + 36];
            for (int slot = 0; slot < javaItems.length; slot++) {
                javaItems[slot] = ItemConverter.bedrockToJavaItem(this.getJavaSlot(JAVA_CONTAINER_WINDOW, slot).get());
            }
            this.player.getJavaSession().send(new ClientboundContainerSetContentPacket(JAVA_CONTAINER_WINDOW, 0, javaItems, carriedItem));
        }
    }

    public void openContainer(int containerId, ContainerType containerType) {
        this.containerId = containerId;
        this.containerType = containerType;
        this.container = null;

        switch (containerType) {
            case CONTAINER:
            case MINECART_CHEST:
            case CHEST_BOAT:
            case DISPENSER:
            case DROPPER:
            case HOPPER:
            case MINECART_HOPPER:
                break;
            default:
                // TODO: furnaces, crafting tables, anvils, ...
                this.closeContainer();
                break;
        }
    }

    // The size of a chest is not known before the server sent what is in it
    private boolean openJavaContainer(int size) {
        org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType javaContainerType;
        String title;
        switch (this.containerType) {
            case DISPENSER:
            case DROPPER:
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.GENERIC_3X3;
                title = this.containerType == ContainerType.DISPENSER ? "container.dispenser" : "container.dropper";
                break;
            case HOPPER:
            case MINECART_HOPPER:
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.HOPPER;
                title = "container.hopper";
                break;
            default:
                if (size % 9 != 0 || size < 9 || size > 54) {
                    this.closeContainer();
                    return false;
                }
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.from(size / 9 - 1);
                title = size > 27 ? "container.chestDouble" : "container.chest";
                break;
        }

        this.container = new ItemData[size];
        this.player.getJavaSession().send(new ClientboundOpenScreenPacket(JAVA_CONTAINER_WINDOW, javaContainerType, Component.translatable(title)));
        return true;
    }

    // Tells the bedrock server that the container it opened is closed
    private void closeContainer() {
        if (this.containerId != ContainerId.NONE) {
            ContainerClosePacket containerClosePacket = new ContainerClosePacket();
            containerClosePacket.setId((byte) this.containerId);
            containerClosePacket.setServerInitiated(false);
            containerClosePacket.setType(this.containerType);
            this.player.getBedrockSession().sendPacket(containerClosePacket);
        }

        this.containerId = ContainerId.NONE;
        this.containerType = null;
        this.container = null;
    }

    public void onBedrockContainerClose(int containerId, boolean serverInitiated) {
        if (containerId != this.containerId) {
            return;
        }

        if (this.container != null) {
            this.player.getJavaSession().send(new ClientboundContainerClosePacket(JAVA_CONTAINER_WINDOW));
        }
        if (serverInitiated) {
            // The server waits for the client to confirm
            this.closeContainer();
        } else {
            this.containerId = ContainerId.NONE;
            this.containerType = null;
            this.container = null;
        }
    }

    public void onJavaWindowClose(int windowId) {
        // A java server puts the item on the cursor back in the inventory, or drops it
        Slot cursorSlot = this.getCursorSlot();
        if (!cursorSlot.isEmpty()) {
            this.quickMove(cursorSlot, this.getPlayerSlots(true));
            if (!cursorSlot.isEmpty()) {
                this.drop(cursorSlot, cursorSlot.get().getCount());
            }
            this.sendRequest();
        }

        if (windowId == JAVA_CONTAINER_WINDOW) {
            this.closeContainer();
        }
        this.sendContents();
    }

    public void onBedrockHeldSlot(int slot) {
        this.heldSlot = slot;
        this.player.getJavaSession().send(new ClientboundSetHeldSlotPacket(slot));
    }

    public void setHeldSlot(int slot) {
        this.heldSlot = slot;

        MobEquipmentPacket mobEquipmentPacket = new MobEquipmentPacket();
        mobEquipmentPacket.setRuntimeEntityId(this.player.getRuntimeEntityId());
        mobEquipmentPacket.setItem(this.getHeldItemSlot().get());
        mobEquipmentPacket.setInventorySlot(slot);
        mobEquipmentPacket.setHotbarSlot(slot);
        mobEquipmentPacket.setContainerId(ContainerId.INVENTORY);
        this.player.getBedrockSession().sendPacket(mobEquipmentPacket);
    }

    public void setCreativeItems(List<CreativeItemData> creativeItems) {
        this.creativeItems.clear();
        for (CreativeItemData creativeItem : creativeItems) {
            String bedrockName = creativeItem.getItem().getDefinition().getIdentifier();
            this.creativeItems.putIfAbsent(bedrockName + ":" + creativeItem.getItem().getDamage(), creativeItem);
            this.creativeItems.putIfAbsent(bedrockName, creativeItem);
        }
    }

    // The slots of the player a shift clicked item goes to, in the order a java server fills them
    private List<Slot> getPlayerSlots(boolean hotbarFirst) {
        List<Slot> slots = new ArrayList<>();
        for (int slot = hotbarFirst ? 0 : 9; slot < (hotbarFirst ? 9 : 36); slot++) {
            slots.add(this.inventorySlot(slot));
        }
        for (int slot = hotbarFirst ? 9 : 0; slot < (hotbarFirst ? 36 : 9); slot++) {
            slots.add(this.inventorySlot(slot));
        }
        return slots;
    }

    private static boolean canStack(ItemData item, ItemData other) {
        return item.getDefinition().getRuntimeId() == other.getDefinition().getRuntimeId() && item.getDamage() == other.getDamage() && Objects.equals(item.getTag(), other.getTag());
    }

    public void leftClick(Slot slot) {
        Slot cursorSlot = this.getCursorSlot();
        if (cursorSlot.isEmpty()) {
            if (!slot.isEmpty()) {
                this.move(slot, cursorSlot, slot.get().getCount());
            }
        } else if (slot.isEmpty()) {
            this.move(cursorSlot, slot, cursorSlot.get().getCount());
        } else if (canStack(cursorSlot.get(), slot.get())) {
            int count = Math.min(cursorSlot.get().getCount(), ItemConverter.getMaxStackSize(slot.get()) - slot.get().getCount());
            if (count > 0) {
                this.move(cursorSlot, slot, count);
            }
        } else {
            this.swap(cursorSlot, slot);
        }
        this.sendRequest();
    }

    public void rightClick(Slot slot) {
        Slot cursorSlot = this.getCursorSlot();
        if (cursorSlot.isEmpty()) {
            if (!slot.isEmpty()) {
                this.move(slot, cursorSlot, (slot.get().getCount() + 1) / 2);
            }
        } else if (slot.isEmpty() || canStack(cursorSlot.get(), slot.get())) {
            if (slot.get().getCount() < ItemConverter.getMaxStackSize(cursorSlot.get())) {
                this.move(cursorSlot, slot, 1);
            }
        } else {
            this.swap(cursorSlot, slot);
        }
        this.sendRequest();
    }

    public void shiftClick(int windowId, int javaSlot) {
        Slot slot = this.getJavaSlot(windowId, javaSlot);
        if (slot == null || slot.isEmpty()) {
            return;
        }

        List<Slot> targets = new ArrayList<>();
        if (windowId == JAVA_CONTAINER_WINDOW && slot.contents() != this.container) {
            for (int containerSlot = 0; containerSlot < this.container.length; containerSlot++) {
                targets.add(this.getJavaSlot(windowId, containerSlot));
            }
        } else if (windowId == JAVA_CONTAINER_WINDOW) {
            // Out of a container the hotbar is filled first, from the right
            targets = this.getPlayerSlots(false);
            Collections.reverse(targets);
        } else if (slot.contents() == this.items && slot.index() >= 9) {
            targets = this.getPlayerSlots(true).subList(0, 9);
        } else if (slot.contents() == this.items) {
            targets = this.getPlayerSlots(false).subList(0, 27);
        } else {
            targets = this.getPlayerSlots(false);
        }

        this.quickMove(slot, targets);
        this.sendRequest();
    }

    // Stacks of the same item are filled up before an empty slot is used
    private void quickMove(Slot source, List<Slot> targets) {
        for (Slot target : targets) {
            if (!source.isEmpty() && !target.isEmpty() && canStack(source.get(), target.get())) {
                int count = Math.min(source.get().getCount(), ItemConverter.getMaxStackSize(target.get()) - target.get().getCount());
                if (count > 0) {
                    this.move(source, target, count);
                }
            }
        }
        for (Slot target : targets) {
            if (!source.isEmpty() && target.isEmpty()) {
                this.move(source, target, source.get().getCount());
            }
        }
    }

    // A double click collects the items that stack with the one on the cursor, starting with the incomplete stacks
    public void doubleClick(int windowId) {
        Slot cursorSlot = this.getCursorSlot();
        for (int pass = 0; pass < 2 && !cursorSlot.isEmpty(); pass++) {
            for (Slot slot : this.getJavaSlots(windowId)) {
                int maxStackSize = ItemConverter.getMaxStackSize(cursorSlot.get());
                if (!slot.isEmpty() && canStack(cursorSlot.get(), slot.get()) && cursorSlot.get().getCount() < maxStackSize && (pass == 1 || slot.get().getCount() < maxStackSize)) {
                    this.move(slot, cursorSlot, Math.min(slot.get().getCount(), maxStackSize - cursorSlot.get().getCount()));
                }
            }
        }
        this.sendRequest();
    }

    public void startDrag() {
        this.dragSlots.clear();
    }

    public void addDragSlot(Slot slot) {
        if (!this.dragSlots.contains(slot)) {
            this.dragSlots.add(slot);
        }
    }

    // The left button spreads the items on the cursor evenly over the slots it was dragged over, the right button leaves one in each
    public void endDrag(boolean spreadEvenly) {
        Slot cursorSlot = this.getCursorSlot();
        if (!cursorSlot.isEmpty() && !this.dragSlots.isEmpty()) {
            int countPerSlot = spreadEvenly ? cursorSlot.get().getCount() / this.dragSlots.size() : 1;
            for (Slot slot : this.dragSlots) {
                if (cursorSlot.isEmpty() || (!slot.isEmpty() && !canStack(cursorSlot.get(), slot.get()))) {
                    continue;
                }

                int count = Math.min(Math.min(countPerSlot, cursorSlot.get().getCount()), ItemConverter.getMaxStackSize(cursorSlot.get()) - slot.get().getCount());
                if (count > 0) {
                    this.move(cursorSlot, slot, count);
                }
            }
            this.sendRequest();
        }
        this.dragSlots.clear();
    }

    private List<Slot> getJavaSlots(int windowId) {
        List<Slot> slots = new ArrayList<>();
        int size = windowId == JAVA_CONTAINER_WINDOW && this.container != null ? this.container.length + 36 : 46;
        for (int javaSlot = 0; javaSlot < size; javaSlot++) {
            Slot slot = this.getJavaSlot(windowId, javaSlot);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public void swapSlots(Slot slot, Slot other) {
        if (slot.isEmpty() && !other.isEmpty()) {
            this.move(other, slot, other.get().getCount());
        } else if (!slot.isEmpty() && other.isEmpty()) {
            this.move(slot, other, slot.get().getCount());
        } else if (!slot.isEmpty()) {
            this.swap(slot, other);
        }
        this.sendRequest();
    }

    public void dropItem(Slot slot, boolean wholeStack) {
        if (!slot.isEmpty()) {
            this.drop(slot, wholeStack ? slot.get().getCount() : 1);
            this.sendRequest();
        }
    }

    // The creative inventory of java tells the server what a slot holds instead of what was clicked
    public void setCreativeItem(Slot slot, ItemStack javaItem) {
        if (javaItem != null && !slot.isEmpty() && javaItem.getId() == ItemConverter.bedrockToJavaItemId(slot.get()) && javaItem.getAmount() == slot.get().getCount()) {
            return;
        }

        if (!slot.isEmpty()) {
            this.requestActions.add(new DestroyAction(slot.get().getCount(), slot.toNetwork()));
            this.change(slot, ItemData.AIR);
            this.sendRequest();
        }

        if (javaItem == null || javaItem.getAmount() <= 0) {
            return;
        }
        CreativeItemData creativeItem = this.creativeItems.get(ItemConverter.javaToBedrockItem(javaItem.getId()));
        if (creativeItem == null) {
            // The server does not offer this item, the client is shown that the slot stayed empty
            this.sendSlot(slot);
            return;
        }

        // The server crafts a full stack, what is not taken from it is discarded
        int count = Math.min(javaItem.getAmount(), ItemConverter.getMaxStackSize(creativeItem.getItem()));
        this.createdOutput[0] = this.withCount(creativeItem.getItem(), count);
        this.requestActions.add(new CraftCreativeAction(creativeItem.getNetId(), 1));
        this.move(new Slot(ContainerSlotType.CREATED_OUTPUT, CREATED_OUTPUT_SLOT, this.createdOutput, 0), slot, count);
        this.sendRequest();
    }

    private ItemData withCount(ItemData item, int count) {
        // A stack this request changed is referred to by the id of the request until the server answered
        return count <= 0 ? ItemData.AIR : item.toBuilder().count(count).usingNetId(true).netId(this.requestId).build();
    }

    private void change(Slot slot, ItemData item) {
        this.requestChanges.putIfAbsent(slot, slot.get());
        slot.contents()[slot.index()] = item;
    }

    private void move(Slot source, Slot destination, int count) {
        ItemData item = source.get();
        ItemStackRequestSlotData from = source.toNetwork();
        ItemStackRequestSlotData to = destination.toNetwork();
        this.requestActions.add(destination.type() == ContainerSlotType.CURSOR ? new TakeAction(count, from, to) : new PlaceAction(count, from, to));

        this.change(destination, this.withCount(item, destination.get().getCount() + count));
        this.change(source, this.withCount(item, item.getCount() - count));
    }

    private void swap(Slot slot, Slot other) {
        ItemData item = slot.get();
        this.requestActions.add(new SwapAction(slot.toNetwork(), other.toNetwork()));

        this.change(slot, this.withCount(other.get(), other.get().getCount()));
        this.change(other, this.withCount(item, item.getCount()));
    }

    private void drop(Slot source, int count) {
        this.requestActions.add(new DropAction(count, source.toNetwork(), false));
        this.change(source, this.withCount(source.get(), source.get().getCount() - count));
    }

    private void sendRequest() {
        if (this.requestActions.isEmpty()) {
            return;
        }

        ItemStackRequestPacket itemStackRequestPacket = new ItemStackRequestPacket();
        itemStackRequestPacket.getRequests().add(new ItemStackRequest(this.requestId, this.requestActions.toArray(new ItemStackRequestAction[0]), new String[0]));
        this.player.getBedrockSession().sendPacket(itemStackRequestPacket);

        this.pendingRequests.put(this.requestId, this.requestChanges);
        this.requestChanges = new HashMap<>();
        this.requestActions.clear();
        // Bedrock clients count their requests down in odd numbers
        this.requestId -= 2;
    }

    public void onResponse(ItemStackResponse response) {
        Map<Slot, ItemData> changes = this.pendingRequests.remove(response.getRequestId());
        if (changes == null) {
            return;
        }

        if (response.getResult() != ItemStackResponseStatus.OK) {
            for (Map.Entry<Slot, ItemData> change : changes.entrySet()) {
                change.getKey().contents()[change.getKey().index()] = change.getValue();
            }
            this.sendContents();
            return;
        }

        // The server tells how many items the slots hold now and which ids it gave the stacks
        for (ItemStackResponseContainer responseContainer : response.getContainers()) {
            for (ItemStackResponseSlot responseSlot : responseContainer.getItems()) {
                Slot slot = this.getBedrockSlot(responseContainer.getContainer(), responseSlot.getSlot());
                if (slot == null || slot.isEmpty()) {
                    continue;
                }

                boolean unexpected = responseSlot.getCount() != slot.get().getCount();
                if (responseSlot.getCount() <= 0) {
                    slot.contents()[slot.index()] = ItemData.AIR;
                } else {
                    slot.contents()[slot.index()] = slot.get().toBuilder().count(responseSlot.getCount()).usingNetId(true).netId(responseSlot.getStackNetworkId()).build();
                }
                if (unexpected) {
                    this.sendSlot(slot);
                }
            }
        }
    }
}
