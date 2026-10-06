/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.network.converter.EnchantmentConverter;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftCreativeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
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
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Inventory {

    // Id the java client is given for the container the bedrock server opened
    public static final int JAVA_CONTAINER_WINDOW = 1;

    // Slots of the bedrock container the cursor and everything that is being worked on are in
    private static final int CURSOR_SLOT = 0;
    private static final int ANVIL_INPUT_SLOT = 1;
    private static final int ANVIL_MATERIAL_SLOT = 2;
    private static final int ENCHANTING_INPUT_SLOT = 14;
    private static final int ENCHANTING_MATERIAL_SLOT = 15;
    private static final int CRAFTING_GRID_SLOT = 28;
    private static final int CRAFTING_TABLE_GRID_SLOT = 32;
    // Where the bedrock server puts what was crafted, enchanted or taken from the creative inventory
    private static final int CREATED_OUTPUT_SLOT = 50;

    private static final int ITEM_USE_CLICK_AIR = 1;
    private static final int ITEM_RELEASE_RELEASE = 0;
    // The server only lets an item be eaten or drunk once enough of its own ticks went by
    private static final int CONSUME_DELAY_TICKS = 2;
    // The bedrock items the server is told about when the player lets go of them
    private static final Set<String> BEDROCK_RELEASED_ITEMS = Set.of("minecraft:bow", "minecraft:crossbow", "minecraft:trident");

    // Data of the java furnace and enchanting table windows
    private static final int JAVA_FURNACE_LIT_TIME = 0;
    private static final int JAVA_FURNACE_LIT_DURATION = 1;
    private static final int JAVA_FURNACE_COOK_TIME = 2;
    private static final int JAVA_FURNACE_COOK_DURATION = 3;
    private static final int JAVA_ENCHANTMENT_HINT = 4;
    private static final int JAVA_ENCHANTMENT_LEVEL_HINT = 7;

    private final Player player;

    // What the bedrock server holds, with the changes requested from it already applied
    private final ItemData[] items = new ItemData[36];
    private final ItemData[] armor = new ItemData[4];
    private final ItemData[] offhand = new ItemData[1];
    private final ItemData[] ui = new ItemData[CREATED_OUTPUT_SLOT + 1];
    private ItemData[] container = null;
    private int containerId = ContainerId.NONE;
    private ContainerType containerType = null;
    // The slots of the open container in the order java has them, null as long as the client is not shown it
    private List<Slot> containerSlots = null;

    // What the crafting grid or the anvil makes. Bedrock clients work that out themselves, java clients are told
    private final ItemData[] result = new ItemData[1];
    private final CraftingRecipes craftingRecipes = new CraftingRecipes();
    private CraftingRecipes.Match craftingMatch = null;
    private List<EnchantOptionData> enchantOptions = Collections.emptyList();
    private String anvilName = "";

    @Getter
    private int heldSlot = 0;

    private final List<Slot> dragSlots = new ArrayList<>();

    // Keyed like the bedrock items of the ItemConverter
    private final Map<String, CreativeItemData> creativeItems = new HashMap<>();

    private int requestId = -1;
    private final List<ItemStackRequestAction> requestActions = new ArrayList<>();
    private String requestText = null;
    private Map<Slot, ItemData> requestChanges = new HashMap<>();
    // The items a request replaced, they are put back if the server refuses it
    private final Map<Integer, Map<Slot, ItemData>> pendingRequests = new HashMap<>();
    // The slots an anvil was asked to put what it made in, the server answers with how damaged that is
    private final Map<Integer, Slot> pendingRepairs = new HashMap<>();

    // The item that is being used, and the ticks until it is eaten or drunk
    private ItemData usedItem = null;
    private final AtomicInteger consumeTicks = new AtomicInteger();

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

    private Slot uiSlot(ContainerSlotType type, int slot) {
        return new Slot(type, slot, this.ui, slot);
    }

    private Slot containerSlot(ContainerSlotType type, int slot) {
        return new Slot(type, slot, this.container, slot);
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
        return this.uiSlot(ContainerSlotType.CURSOR, CURSOR_SLOT);
    }

    // Not a slot of the bedrock server, see the result field
    private Slot getResultSlot() {
        return new Slot(ContainerSlotType.CRAFTING_OUTPUT, 0, this.result, 0);
    }

    public boolean isResultSlot(Slot slot) {
        return slot.contents() == this.result;
    }

    public Slot getJavaSlot(int windowId, int slot) {
        if (windowId == 0) {
            if (slot == 0) {
                return this.getResultSlot();
            } else if (slot >= 1 && slot <= 4) {
                return this.uiSlot(ContainerSlotType.CRAFTING_INPUT, CRAFTING_GRID_SLOT + slot - 1);
            } else if (slot >= 5 && slot <= 8) {
                return new Slot(ContainerSlotType.ARMOR, slot - 5, this.armor, slot - 5);
            } else if (slot >= 9 && slot <= 35) {
                return this.inventorySlot(slot);
            } else if (slot >= 36 && slot <= 44) {
                return this.inventorySlot(slot - 36);
            } else if (slot == 45) {
                return this.getOffhandSlot();
            }
        } else if (windowId == JAVA_CONTAINER_WINDOW && this.containerSlots != null && slot >= 0) {
            // A container is followed by the main inventory and the hotbar
            int size = this.containerSlots.size();
            if (slot < size) {
                return this.containerSlots.get(slot);
            } else if (slot < size + 27) {
                return this.inventorySlot(slot - size + 9);
            } else if (slot < size + 36) {
                return this.inventorySlot(slot - size - 27);
            }
        }

        return null;
    }

    private List<Slot> getJavaSlots(int windowId) {
        List<Slot> slots = new ArrayList<>();
        int size = windowId == JAVA_CONTAINER_WINDOW && this.containerSlots != null ? this.containerSlots.size() + 36 : 46;
        for (int javaSlot = 0; javaSlot < size; javaSlot++) {
            Slot slot = this.getJavaSlot(windowId, javaSlot);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
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
            case CRAFTING_INPUT:
            case ANVIL_INPUT:
            case ANVIL_MATERIAL:
            case ENCHANTING_INPUT:
            case ENCHANTING_MATERIAL:
            case CREATED_OUTPUT:
                return slot >= 0 && slot < this.ui.length ? this.uiSlot(type, slot) : null;
            default:
                return this.container != null && slot >= 0 && slot < this.container.length ? this.containerSlot(type, slot) : null;
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
            return this.ui;
        } else if (containerId == this.containerId) {
            return this.container;
        }
        return null;
    }

    public void setContents(int containerId, List<ItemData> contents) {
        // The size of a chest is not known before the server sent what is in it
        if (containerId == this.containerId && this.containerType != null && this.containerSlots == null && !this.openJavaContainer(contents.size())) {
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
        if (containerId == this.containerId && container != null && container.length == 0) {
            // What is put in a crafting table, an anvil or an enchanting table is held by the player
            container = this.ui;
        }
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
        } else if (contents == this.ui && slot == CURSOR_SLOT) {
            this.player.getJavaSession().send(new ClientboundSetCursorItemPacket(javaItem));
        } else {
            // A slot of the open container or of a crafting grid, what is made of it may have changed with it
            ItemData lastResult = this.result[0];
            this.updateResult();
            boolean resultChanged = !Objects.equals(lastResult, this.result[0]);
            for (int windowId = 0; windowId <= JAVA_CONTAINER_WINDOW; windowId++) {
                List<Slot> slots = windowId == 0 ? this.getJavaSlots(0).subList(0, 5) : this.containerSlots;
                for (int javaSlot = 0; slots != null && javaSlot < slots.size(); javaSlot++) {
                    Slot javaContainerSlot = slots.get(javaSlot);
                    if ((resultChanged && this.isResultSlot(javaContainerSlot)) || (javaContainerSlot.contents() == contents && javaContainerSlot.index() == slot)) {
                        this.player.getJavaSession().send(new ClientboundContainerSetSlotPacket(windowId, 0, javaSlot, ItemConverter.bedrockToJavaItem(javaContainerSlot.get())));
                    }
                }
            }
        }
    }

    public void sendContents() {
        this.updateResult();
        ItemStack carriedItem = ItemConverter.bedrockToJavaItem(this.ui[CURSOR_SLOT]);

        for (int windowId = 0; windowId <= JAVA_CONTAINER_WINDOW; windowId++) {
            if (windowId == JAVA_CONTAINER_WINDOW && this.containerSlots == null) {
                break;
            }

            ItemStack[] javaItems = new ItemStack[windowId == 0 ? 46 : this.containerSlots.size() + 36];
            for (int slot = 0; slot < javaItems.length; slot++) {
                javaItems[slot] = ItemConverter.bedrockToJavaItem(this.getJavaSlot(windowId, slot).get());
            }
            this.player.getJavaSession().send(new ClientboundContainerSetContentPacket(windowId, 0, javaItems, carriedItem));
        }
    }

    public void openContainer(int containerId, ContainerType containerType) {
        this.containerId = containerId;
        this.containerType = containerType;
        this.container = null;
        this.containerSlots = null;

        switch (containerType) {
            case CONTAINER:
            case MINECART_CHEST:
            case CHEST_BOAT:
                // Shown once the server sent what is in it, which tells how large it is
                break;
            case DISPENSER:
            case DROPPER:
                this.openJavaContainer(9);
                break;
            case HOPPER:
            case MINECART_HOPPER:
            case BREWING_STAND:
                this.openJavaContainer(5);
                break;
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
                this.openJavaContainer(3);
                break;
            case WORKBENCH:
            case ENCHANTMENT:
            case ANVIL:
                // What is put in these is held by the player
                this.openJavaContainer(0);
                break;
            default:
                // TODO: looms, grindstones, smithing tables, stonecutters, beacons, villagers, ...
                this.closeContainer();
                break;
        }
    }

    private boolean openJavaContainer(int size) {
        org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType javaContainerType;
        String title;
        this.container = new ItemData[size];
        List<Slot> slots = new ArrayList<>();
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
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
                if (this.containerType == ContainerType.FURNACE) {
                    javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.FURNACE;
                    title = "container.furnace";
                    slots.add(this.containerSlot(ContainerSlotType.FURNACE_INGREDIENT, 0));
                } else if (this.containerType == ContainerType.BLAST_FURNACE) {
                    javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.BLAST_FURNACE;
                    title = "container.blast_furnace";
                    slots.add(this.containerSlot(ContainerSlotType.BLAST_FURNACE_INGREDIENT, 0));
                } else {
                    javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.SMOKER;
                    title = "container.smoker";
                    slots.add(this.containerSlot(ContainerSlotType.SMOKER_INGREDIENT, 0));
                }
                slots.add(this.containerSlot(ContainerSlotType.FURNACE_FUEL, 1));
                slots.add(this.containerSlot(ContainerSlotType.FURNACE_RESULT, 2));
                break;
            case BREWING_STAND:
                // Java has the bottles before the ingredient
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.BREWING_STAND;
                title = "container.brewing";
                slots.add(this.containerSlot(ContainerSlotType.BREWING_RESULT, 1));
                slots.add(this.containerSlot(ContainerSlotType.BREWING_RESULT, 2));
                slots.add(this.containerSlot(ContainerSlotType.BREWING_RESULT, 3));
                slots.add(this.containerSlot(ContainerSlotType.BREWING_INPUT, 0));
                slots.add(this.containerSlot(ContainerSlotType.BREWING_FUEL, 4));
                break;
            case WORKBENCH:
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.CRAFTING;
                title = "container.crafting";
                slots.add(this.getResultSlot());
                for (int slot = 0; slot < 9; slot++) {
                    slots.add(this.uiSlot(ContainerSlotType.CRAFTING_INPUT, CRAFTING_TABLE_GRID_SLOT + slot));
                }
                break;
            case ENCHANTMENT:
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.ENCHANTMENT;
                title = "container.enchant";
                slots.add(this.uiSlot(ContainerSlotType.ENCHANTING_INPUT, ENCHANTING_INPUT_SLOT));
                slots.add(this.uiSlot(ContainerSlotType.ENCHANTING_MATERIAL, ENCHANTING_MATERIAL_SLOT));
                break;
            case ANVIL:
                javaContainerType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType.ANVIL;
                title = "container.repair";
                slots.add(this.uiSlot(ContainerSlotType.ANVIL_INPUT, ANVIL_INPUT_SLOT));
                slots.add(this.uiSlot(ContainerSlotType.ANVIL_MATERIAL, ANVIL_MATERIAL_SLOT));
                slots.add(this.getResultSlot());
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

        // Every other container has its slots in the order of the bedrock server
        if (slots.isEmpty()) {
            for (int slot = 0; slot < size; slot++) {
                slots.add(this.containerSlot(ContainerSlotType.LEVEL_ENTITY, slot));
            }
        }
        this.containerSlots = slots;
        this.enchantOptions = Collections.emptyList();
        this.anvilName = "";

        this.player.getJavaSession().send(new ClientboundOpenScreenPacket(JAVA_CONTAINER_WINDOW, javaContainerType, Component.translatable(title)));
        if (this.containerType == ContainerType.FURNACE || this.containerType == ContainerType.BLAST_FURNACE || this.containerType == ContainerType.SMOKER) {
            // The bedrock server counts the cook time up to 200 for all of them
            this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, JAVA_FURNACE_COOK_DURATION, 200));
        }
        if (size == 0) {
            this.sendContents();
        }
        return true;
    }

    // The progress of a furnace or a brewing stand
    public void setContainerData(int containerId, int property, int value) {
        if (containerId != this.containerId || this.containerSlots == null) {
            return;
        }

        int javaProperty;
        if (this.containerType == ContainerType.BREWING_STAND) {
            if (property != ContainerSetDataPacket.BREWING_STAND_BREW_TIME && property != ContainerSetDataPacket.BREWING_STAND_FUEL_AMOUNT) {
                return;
            }
            // Java has the same two first
            javaProperty = property;
        } else if (property == ContainerSetDataPacket.FURNACE_TICK_COUNT) {
            javaProperty = JAVA_FURNACE_COOK_TIME;
        } else if (property == ContainerSetDataPacket.FURNACE_LIT_TIME) {
            javaProperty = JAVA_FURNACE_LIT_TIME;
        } else if (property == ContainerSetDataPacket.FURNACE_LIT_DURATION) {
            javaProperty = JAVA_FURNACE_LIT_DURATION;
        } else {
            return;
        }
        this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, javaProperty, value));
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
        this.forgetContainer();
    }

    private void forgetContainer() {
        // The server puts what was left in a crafting table, an anvil or an enchanting table back in the inventory
        for (int slot = 0; this.containerSlots != null && slot < this.containerSlots.size(); slot++) {
            if (this.containerSlots.get(slot).contents() == this.ui) {
                this.ui[this.containerSlots.get(slot).index()] = ItemData.AIR;
            }
        }

        this.containerId = ContainerId.NONE;
        this.containerType = null;
        this.container = null;
        this.containerSlots = null;
    }

    public void onBedrockContainerClose(int containerId, boolean serverInitiated) {
        if (containerId != this.containerId) {
            return;
        }

        if (this.containerSlots != null) {
            this.player.getJavaSession().send(new ClientboundContainerClosePacket(JAVA_CONTAINER_WINDOW));
        }
        if (serverInitiated) {
            // The server waits for the client to confirm
            this.closeContainer();
        } else {
            this.forgetContainer();
        }
    }

    public void onJavaWindowClose(int windowId) {
        // A java server puts the item on the cursor and what is left in a crafting grid back in the inventory, or drops it
        List<Slot> leftSlots = new ArrayList<>();
        for (Slot slot : this.getJavaSlots(windowId)) {
            if (slot.contents() == this.ui) {
                leftSlots.add(slot);
            }
        }
        leftSlots.add(this.getCursorSlot());
        for (Slot slot : leftSlots) {
            if (!slot.isEmpty()) {
                this.quickMove(slot, this.getPlayerSlots(true));
                if (!slot.isEmpty()) {
                    this.drop(slot, slot.get().getCount());
                }
            }
        }
        this.sendRequest();

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
        if (slot != this.heldSlot) {
            // A java client stops using an item when it holds another one, without telling
            this.releaseItem();
        }
        this.heldSlot = slot;

        MobEquipmentPacket mobEquipmentPacket = new MobEquipmentPacket();
        mobEquipmentPacket.setRuntimeEntityId(this.player.getRuntimeEntityId());
        mobEquipmentPacket.setItem(this.getHeldItemSlot().get());
        mobEquipmentPacket.setInventorySlot(slot);
        mobEquipmentPacket.setHotbarSlot(slot);
        mobEquipmentPacket.setContainerId(ContainerId.INVENTORY);
        this.player.getBedrockSession().sendPacket(mobEquipmentPacket);
    }

    // The player used the item it holds without aiming at a block
    public void useItem() {
        Slot slot = this.getHeldItemSlot();
        this.sendItemUse(slot.get());

        int consumeTicks = slot.isEmpty() ? 0 : ItemConverter.getConsumeTicks(slot.get());
        boolean released = consumeTicks > 0 || (!slot.isEmpty() && BEDROCK_RELEASED_ITEMS.contains(slot.get().getDefinition().getIdentifier()));
        this.usedItem = released ? slot.get() : null;
        this.consumeTicks.set(consumeTicks > 0 ? consumeTicks + CONSUME_DELAY_TICKS : 0);
    }

    private void sendItemUse(ItemData item) {
        InventoryTransactionPacket inventoryTransactionPacket = new InventoryTransactionPacket();
        inventoryTransactionPacket.setTransactionType(InventoryTransactionType.ITEM_USE);
        inventoryTransactionPacket.setActionType(ITEM_USE_CLICK_AIR);
        inventoryTransactionPacket.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
        inventoryTransactionPacket.setBlockPosition(Vector3i.ZERO);
        inventoryTransactionPacket.setBlockFace(255);
        inventoryTransactionPacket.setHotbarSlot(this.heldSlot);
        inventoryTransactionPacket.setHand(HandSlot.MAINHAND);
        inventoryTransactionPacket.setItemInHand(item);
        inventoryTransactionPacket.setPlayerPosition(this.player.getVector3f());
        inventoryTransactionPacket.setClickPosition(Vector3f.ZERO);
        inventoryTransactionPacket.setBlockDefinition(() -> 0);
        inventoryTransactionPacket.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
        this.player.getBedrockSession().sendPacket(inventoryTransactionPacket);
    }

    // Called every tick by the thread that sends what the player does. Returns whether the item is eaten or drunk now
    public boolean tickItemUse() {
        return this.consumeTicks.get() > 0 && this.consumeTicks.decrementAndGet() == 0;
    }

    // A java server knows by itself when the player is done eating or drinking. A bedrock server is told, by the
    // item being used a second time
    public void finishUsingItem() {
        Slot slot = this.getHeldItemSlot();
        if (this.usedItem != null && !slot.isEmpty() && canStack(this.usedItem, slot.get())) {
            this.sendItemUse(slot.get());
        }
        this.usedItem = null;
    }

    // The player stopped using the item before it was used up, which is also what shoots a bow
    public void releaseItem() {
        this.consumeTicks.set(0);
        if (this.usedItem == null) {
            return;
        }
        this.usedItem = null;

        InventoryTransactionPacket inventoryTransactionPacket = new InventoryTransactionPacket();
        inventoryTransactionPacket.setTransactionType(InventoryTransactionType.ITEM_RELEASE);
        inventoryTransactionPacket.setActionType(ITEM_RELEASE_RELEASE);
        inventoryTransactionPacket.setHotbarSlot(this.heldSlot);
        inventoryTransactionPacket.setItemInHand(this.getHeldItemSlot().get());
        inventoryTransactionPacket.setHeadPosition(this.player.getVector3f());
        this.player.getBedrockSession().sendPacket(inventoryTransactionPacket);
    }

    public void setCreativeItems(List<CreativeItemData> creativeItems) {
        this.creativeItems.clear();
        for (CreativeItemData creativeItem : creativeItems) {
            String bedrockName = creativeItem.getItem().getDefinition().getIdentifier();
            this.creativeItems.putIfAbsent(bedrockName + ":" + creativeItem.getItem().getDamage(), creativeItem);
            this.creativeItems.putIfAbsent(bedrockName, creativeItem);
        }
    }

    public CraftingRecipes getCraftingRecipes() {
        return this.craftingRecipes;
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

    private static boolean isIn(ItemData item, String... bedrockNames) {
        for (String bedrockName : bedrockNames) {
            if (item.getDefinition().getIdentifier().equals(bedrockName)) {
                return true;
            }
        }
        return false;
    }

    // Whether a shift click can put the item in a slot of the open container
    private boolean isQuickMoveTarget(Slot slot, ItemData item) {
        switch (slot.type()) {
            case FURNACE_FUEL:
                return ItemConverter.isBedrockFuel(item);
            case FURNACE_INGREDIENT:
            case BLAST_FURNACE_INGREDIENT:
            case SMOKER_INGREDIENT:
                return !ItemConverter.isBedrockFuel(item);
            case BREWING_FUEL:
                return isIn(item, "minecraft:blaze_powder");
            case BREWING_RESULT:
                return isIn(item, "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:glass_bottle");
            case BREWING_INPUT:
                return !isIn(item, "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:glass_bottle");
            case ENCHANTING_MATERIAL:
                return isIn(item, "minecraft:lapis_lazuli");
            case ENCHANTING_INPUT:
                return !isIn(item, "minecraft:lapis_lazuli") && slot.isEmpty();
            case FURNACE_RESULT:
            case CRAFTING_INPUT:
                // A java client does not fill a crafting grid with a shift click
                return false;
            default:
                return !this.isResultSlot(slot);
        }
    }

    // What was made can be taken, nothing can be put there
    private boolean takeOutput(Slot slot) {
        if (this.isResultSlot(slot)) {
            this.takeResult(false);
            return true;
        }
        if (slot.type() != ContainerSlotType.FURNACE_RESULT) {
            return false;
        }

        Slot cursorSlot = this.getCursorSlot();
        if (!slot.isEmpty() && (cursorSlot.isEmpty() || (canStack(cursorSlot.get(), slot.get()) && cursorSlot.get().getCount() + slot.get().getCount() <= ItemConverter.getMaxStackSize(slot.get())))) {
            this.move(slot, cursorSlot, slot.get().getCount());
            this.sendRequest();
        }
        return true;
    }

    public void leftClick(Slot slot) {
        if (this.takeOutput(slot)) {
            return;
        }

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
        if (this.takeOutput(slot)) {
            return;
        }

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
        if (slot == null) {
            return;
        }
        if (this.isResultSlot(slot)) {
            this.takeResult(true);
            return;
        }
        if (slot.isEmpty()) {
            return;
        }

        List<Slot> targets = new ArrayList<>();
        if (windowId == JAVA_CONTAINER_WINDOW && slot.contents() == this.items) {
            for (Slot containerSlot : this.containerSlots) {
                if (this.isQuickMoveTarget(containerSlot, slot.get())) {
                    targets.add(containerSlot);
                }
            }
            // Blaze powder is an ingredient too, a brewing stand is given it as fuel first
            targets.sort(Comparator.comparing(target -> target.type() != ContainerSlotType.BREWING_FUEL));
        }

        if (!targets.isEmpty()) {
            // Into the open container
        } else if (slot.contents() == this.container) {
            // Out of a container the hotbar is filled first, from the right
            targets = this.getPlayerSlots(false);
            Collections.reverse(targets);
        } else if (slot.contents() != this.items) {
            // What is worn, or was put in a crafting grid, an anvil or an enchanting table
            targets = this.getPlayerSlots(false);
        } else if (slot.index() >= 9) {
            targets = this.getPlayerSlots(true).subList(0, 9);
        } else {
            targets = this.getPlayerSlots(false).subList(0, 27);
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
                if (!this.isResultSlot(slot) && !slot.isEmpty() && canStack(cursorSlot.get(), slot.get()) && cursorSlot.get().getCount() < maxStackSize && (pass == 1 || slot.get().getCount() < maxStackSize)) {
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
        if (!this.dragSlots.contains(slot) && !this.isResultSlot(slot) && slot.type() != ContainerSlotType.FURNACE_RESULT) {
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

    public void swapSlots(Slot slot, Slot other) {
        if (this.isResultSlot(slot) || slot.type() == ContainerSlotType.FURNACE_RESULT) {
            return;
        }

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
        if (!slot.isEmpty() && !this.isResultSlot(slot)) {
            this.drop(slot, wholeStack ? slot.get().getCount() : 1);
            this.sendRequest();
        }
    }

    // Whether a java client holds the two for the same item, the potion in it counts
    private static boolean isItem(ItemData item, ItemStack javaItem) {
        ItemStack translatedItem = ItemConverter.bedrockToJavaItem(item);
        return translatedItem.getId() == javaItem.getId() && Objects.equals(ItemConverter.javaToBedrockItem(translatedItem), ItemConverter.javaToBedrockItem(javaItem));
    }

    // The creative inventory of java tells the server what a slot holds instead of what was clicked
    public void setCreativeItem(Slot slot, ItemStack javaItem) {
        if (this.isResultSlot(slot) || (javaItem != null && !slot.isEmpty() && isItem(slot.get(), javaItem) && javaItem.getAmount() == slot.get().getCount())) {
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
        CreativeItemData creativeItem = this.creativeItems.get(ItemConverter.javaToBedrockItem(javaItem));
        if (creativeItem == null) {
            // The server does not offer this item, the client is shown that the slot stayed empty
            this.sendSlot(slot);
            return;
        }

        // The server crafts a full stack, what is not taken from it is discarded
        int count = Math.min(javaItem.getAmount(), ItemConverter.getMaxStackSize(creativeItem.getItem()));
        this.requestActions.add(new CraftCreativeAction(creativeItem.getNetId(), 1));
        this.move(this.createOutput(creativeItem.getItem(), count), slot, count);
        this.sendRequest();
    }

    private List<Slot> getCraftingGrid() {
        List<Slot> grid = new ArrayList<>();
        boolean craftingTable = this.containerType == ContainerType.WORKBENCH && this.containerSlots != null;
        for (int slot = 0; slot < (craftingTable ? 9 : 4); slot++) {
            grid.add(this.uiSlot(ContainerSlotType.CRAFTING_INPUT, (craftingTable ? CRAFTING_TABLE_GRID_SLOT : CRAFTING_GRID_SLOT) + slot));
        }
        return grid;
    }

    private void updateResult() {
        if (this.containerType == ContainerType.ANVIL && this.containerSlots != null) {
            this.updateAnvilResult();
            return;
        }

        List<Slot> grid = this.getCraftingGrid();
        ItemData[] gridItems = new ItemData[grid.size()];
        for (int slot = 0; slot < gridItems.length; slot++) {
            gridItems[slot] = grid.get(slot).get();
        }
        this.craftingMatch = this.craftingRecipes.find(gridItems, gridItems.length == 9 ? 3 : 2);
        this.result[0] = this.craftingMatch == null ? ItemData.AIR : this.craftingMatch.results().get(0);
    }

    // A click on what the crafting grid or the anvil makes
    public void takeResult(boolean asManyAsPossible) {
        this.updateResult();
        if (this.containerType == ContainerType.ANVIL && this.containerSlots != null) {
            this.takeAnvilResult();
            return;
        }
        if (this.craftingMatch == null) {
            return;
        }

        ItemData craftedItem = this.craftingMatch.results().get(0);
        List<Slot> grid = this.getCraftingGrid();
        Slot cursorSlot = this.getCursorSlot();
        int crafts = 1;
        if (asManyAsPossible) {
            // A shift click, limited to one stack
            crafts = Math.max(1, ItemConverter.getMaxStackSize(craftedItem) / craftedItem.getCount());
            for (Slot slot : grid) {
                if (!slot.isEmpty()) {
                    crafts = Math.min(crafts, slot.get().getCount());
                }
            }
        } else if (!cursorSlot.isEmpty() && (!canStack(cursorSlot.get(), craftedItem) || cursorSlot.get().getCount() + craftedItem.getCount() > ItemConverter.getMaxStackSize(craftedItem))) {
            return;
        }

        this.requestActions.add(new CraftRecipeAction(this.craftingMatch.networkId(), crafts));
        this.requestActions.add(new CraftResultsDeprecatedAction(this.craftingMatch.results().toArray(new ItemData[0]), crafts));
        for (Slot slot : grid) {
            if (!slot.isEmpty()) {
                this.requestActions.add(new ConsumeAction(crafts, slot.toNetwork()));
                this.change(slot, this.withCount(slot.get(), slot.get().getCount() - crafts));
            }
        }

        Slot createdOutput = this.createOutput(craftedItem, craftedItem.getCount() * crafts);
        if (asManyAsPossible) {
            List<Slot> targets = this.getPlayerSlots(false);
            Collections.reverse(targets);
            this.quickMove(createdOutput, targets);
            if (!createdOutput.isEmpty()) {
                this.drop(createdOutput, createdOutput.get().getCount());
            }
        } else {
            this.move(createdOutput, cursorSlot, createdOutput.get().getCount());
        }
        this.sendRequest();
    }

    public void setEnchantOptions(List<EnchantOptionData> enchantOptions) {
        this.enchantOptions = enchantOptions;
        if (this.containerType != ContainerType.ENCHANTMENT || this.containerSlots == null) {
            return;
        }

        // The levels the three buttons need, and one of the enchantments each of them gives
        for (int button = 0; button < 3; button++) {
            int cost = 0, hint = -1, levelHint = -1;
            if (button < enchantOptions.size()) {
                cost = enchantOptions.get(button).getCost();
                for (Map.Entry<Integer, Integer> enchantment : getEnchantments(enchantOptions.get(button)).entrySet()) {
                    if (hint == -1 && EnchantmentConverter.bedrockToJavaEnchantmentId(enchantment.getKey()) != -1) {
                        hint = EnchantmentConverter.bedrockToJavaEnchantmentId(enchantment.getKey());
                        levelHint = enchantment.getValue();
                    }
                }
            }
            this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, button, cost));
            this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, JAVA_ENCHANTMENT_HINT + button, hint));
            this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, JAVA_ENCHANTMENT_LEVEL_HINT + button, levelHint));
        }
    }

    // The server lists the enchantments of an option by where they take effect
    private static Map<Integer, Integer> getEnchantments(EnchantOptionData enchantOption) {
        Map<Integer, Integer> enchantments = new LinkedHashMap<>();
        for (List<EnchantData> list : List.of(enchantOption.getEnchants0(), enchantOption.getEnchants1(), enchantOption.getEnchants2())) {
            for (EnchantData enchantment : list) {
                enchantments.put(enchantment.getType(), enchantment.getLevel());
            }
        }
        return enchantments;
    }

    private ItemDefinition getItemDefinition(String bedrockName) {
        for (ItemDefinition itemDefinition : this.player.getItemDefinitions().values()) {
            if (itemDefinition.getIdentifier().equals(bedrockName)) {
                return itemDefinition;
            }
        }
        return null;
    }

    // A button of the enchanting table
    public void clickButton(int button) {
        if (this.containerType != ContainerType.ENCHANTMENT || this.containerSlots == null || button < 0 || button >= this.enchantOptions.size()) {
            return;
        }

        Slot input = this.containerSlots.get(0);
        Slot material = this.containerSlots.get(1);
        boolean creative = this.player.getGameMode() == GameType.CREATIVE;
        if (input.isEmpty() || (!creative && material.get().getCount() < button + 1)) {
            return;
        }

        // The server does not tell what the item became, a bedrock client enchants it the same way itself
        EnchantOptionData enchantOption = this.enchantOptions.get(button);
        ItemData.Builder enchantedItem = input.get().toBuilder().tag(EnchantmentConverter.setEnchantments(input.get().getTag(), getEnchantments(enchantOption)));
        ItemDefinition enchantedBook = this.getItemDefinition("minecraft:enchanted_book");
        if (enchantedBook != null && isIn(input.get(), "minecraft:book")) {
            enchantedItem.definition(enchantedBook);
        }

        ItemData item = enchantedItem.build();
        this.requestActions.add(new CraftRecipeAction(enchantOption.getEnchantNetId(), 1));
        this.requestActions.add(new CraftResultsDeprecatedAction(new ItemData[]{item}, 1));
        this.requestActions.add(new ConsumeAction(item.getCount(), input.toNetwork()));
        this.change(input, ItemData.AIR);
        if (!creative) {
            this.requestActions.add(new ConsumeAction(button + 1, material.toNetwork()));
            this.change(material, this.withCount(material.get(), material.get().getCount() - button - 1));
        }
        this.move(this.createOutput(item, item.getCount()), input, item.getCount());
        this.sendRequest();

        this.enchantOptions = Collections.emptyList();
        this.sendContents();
    }

    private static int getRepairCost(ItemData item) {
        return ItemConverter.isEmpty(item) || item.getTag() == null ? 0 : item.getTag().getInt("RepairCost");
    }

    private static String getCustomName(ItemData item) {
        NbtMap display = item.getTag() == null ? null : item.getTag().getCompound("display", null);
        return display != null && display.containsKey("Name", NbtType.STRING) ? display.getString("Name") : "";
    }

    public void setAnvilName(String anvilName) {
        this.anvilName = anvilName == null ? "" : anvilName;
        if (this.containerType == ContainerType.ANVIL && this.containerSlots != null) {
            this.updateResult();
            this.player.getJavaSession().send(new ClientboundContainerSetSlotPacket(JAVA_CONTAINER_WINDOW, 0, 2, ItemConverter.bedrockToJavaItem(this.result[0])));
        }
    }

    // The bedrock server does not tell what an anvil makes, a bedrock client works that out itself. The client is
    // shown the item with its new name and the enchantments of both items. How much a repair mends is only known
    // once the server answered, and the levels shown are those of a rename, a repair can cost more
    private void updateAnvilResult() {
        ItemData input = this.ui[ANVIL_INPUT_SLOT];
        ItemData material = this.ui[ANVIL_MATERIAL_SLOT];
        // Without a name the item loses the one it was given before
        boolean renamed = !ItemConverter.isEmpty(input) && !this.anvilName.equals(getCustomName(input));
        int cost = 0;
        if (ItemConverter.isEmpty(input) || (!renamed && ItemConverter.isEmpty(material))) {
            this.result[0] = ItemData.AIR;
        } else {
            NbtMap tag = input.getTag() == null ? NbtMap.EMPTY : input.getTag();
            if (renamed) {
                NbtMapBuilder display = tag.getCompound("display", NbtMap.EMPTY).toBuilder();
                NbtMapBuilder renamedTag = tag.toBuilder();
                if (this.anvilName.isEmpty()) {
                    display.remove("Name");
                } else {
                    display.putString("Name", this.anvilName);
                }
                if (display.isEmpty()) {
                    renamedTag.remove("display");
                } else {
                    renamedTag.putCompound("display", display.build());
                }
                tag = renamedTag.build();
            }

            // An enchanted book or a second item of the same kind adds its enchantments
            if (!ItemConverter.isEmpty(material) && (isIn(material, "minecraft:enchanted_book") || material.getDefinition().getRuntimeId() == input.getDefinition().getRuntimeId())) {
                Map<Integer, Integer> enchantments = EnchantmentConverter.getEnchantments(tag);
                for (Map.Entry<Integer, Integer> enchantment : EnchantmentConverter.getEnchantments(material.getTag()).entrySet()) {
                    int level = enchantments.getOrDefault(enchantment.getKey(), 0);
                    level = level == enchantment.getValue() ? level + 1 : Math.max(level, enchantment.getValue());
                    enchantments.put(enchantment.getKey(), Math.min(level, EnchantmentConverter.getMaxLevel(enchantment.getKey())));
                }
                if (!enchantments.isEmpty()) {
                    tag = EnchantmentConverter.setEnchantments(tag, enchantments);
                }
            }

            this.result[0] = input.toBuilder().tag(tag.isEmpty() ? null : tag).build();
            cost = getRepairCost(input) + getRepairCost(material) + (renamed ? 1 : 0) + (ItemConverter.isEmpty(material) ? 0 : 1);
        }
        this.player.getJavaSession().send(new ClientboundContainerSetDataPacket(JAVA_CONTAINER_WINDOW, 0, cost));
    }

    private void takeAnvilResult() {
        Slot input = this.uiSlot(ContainerSlotType.ANVIL_INPUT, ANVIL_INPUT_SLOT);
        Slot material = this.uiSlot(ContainerSlotType.ANVIL_MATERIAL, ANVIL_MATERIAL_SLOT);
        Slot cursorSlot = this.getCursorSlot();
        ItemData anvilResult = this.result[0];
        if (ItemConverter.isEmpty(anvilResult) || !cursorSlot.isEmpty()) {
            return;
        }

        this.requestText = this.anvilName;
        this.requestActions.add(new CraftRecipeOptionalAction(0, 0));
        this.requestActions.add(new CraftResultsDeprecatedAction(new ItemData[]{anvilResult}, 1));
        this.requestActions.add(new ConsumeAction(input.get().getCount(), input.toNetwork()));
        this.change(input, ItemData.AIR);
        if (!material.isEmpty()) {
            // The server decides how much of the material is used up and tells in its answer
            this.requestActions.add(new ConsumeAction(1, material.toNetwork()));
            this.change(material, this.withCount(material.get(), material.get().getCount() - 1));
        }
        this.move(this.createOutput(anvilResult, anvilResult.getCount()), cursorSlot, anvilResult.getCount());
        this.pendingRepairs.put(this.requestId, cursorSlot);
        this.sendRequest();
    }

    private ItemData withCount(ItemData item, int count) {
        // A stack this request changed is referred to by the id of the request until the server answered
        return count <= 0 ? ItemData.AIR : item.toBuilder().count(count).usingNetId(true).netId(this.requestId).build();
    }

    // The slot the server puts what this request makes in
    private Slot createOutput(ItemData item, int count) {
        this.ui[CREATED_OUTPUT_SLOT] = this.withCount(item, count);
        return this.uiSlot(ContainerSlotType.CREATED_OUTPUT, CREATED_OUTPUT_SLOT);
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

        ItemStackRequestAction[] actions = this.requestActions.toArray(new ItemStackRequestAction[0]);
        ItemStackRequestPacket itemStackRequestPacket = new ItemStackRequestPacket();
        if (this.requestText == null) {
            itemStackRequestPacket.getRequests().add(new ItemStackRequest(this.requestId, actions, new String[0]));
        } else {
            itemStackRequestPacket.getRequests().add(new ItemStackRequest(this.requestId, actions, new String[]{this.requestText}, TextProcessingEventOrigin.ANVIL_TEXT));
        }
        this.player.getBedrockSession().sendPacket(itemStackRequestPacket);

        this.pendingRequests.put(this.requestId, this.requestChanges);
        this.requestChanges = new HashMap<>();
        this.requestActions.clear();
        this.requestText = null;
        // Bedrock clients count their requests down in odd numbers
        this.requestId -= 2;
    }

    public void onResponse(ItemStackResponse response) {
        Map<Slot, ItemData> changes = this.pendingRequests.remove(response.getRequestId());
        if (changes == null) {
            return;
        }

        Slot repairedSlot = this.pendingRepairs.remove(response.getRequestId());
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
                if (slot == null) {
                    continue;
                }

                // The server can have used up less than it was expected to, of what is put in an anvil for example
                ItemData item = slot.isEmpty() ? changes.get(slot) : slot.get();
                if (ItemConverter.isEmpty(item)) {
                    continue;
                }

                boolean unexpected = responseSlot.getCount() != slot.get().getCount();
                if (responseSlot.getCount() <= 0) {
                    slot.contents()[slot.index()] = ItemData.AIR;
                } else {
                    ItemData.Builder builder = item.toBuilder().count(responseSlot.getCount()).usingNetId(true).netId(responseSlot.getStackNetworkId());
                    if (slot.equals(repairedSlot) && item.getTag() != null && item.getTag().getInt("Damage") != responseSlot.getDurabilityCorrection()) {
                        builder.tag(item.getTag().toBuilder().putInt("Damage", responseSlot.getDurabilityCorrection()).build());
                        unexpected = true;
                    }
                    slot.contents()[slot.index()] = builder.build();
                }
                if (unexpected) {
                    this.sendSlot(slot);
                }
            }
        }
    }
}
