package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class InventorySlotPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket packet = (org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket) pk;

        FullContainerName container = packet.getContainerNameData();
        if (container != null && container.getContainer() == ContainerSlotType.DYNAMIC_CONTAINER && container.getDynamicId() != null) {
            player.getInventory().setBundleSlot(container.getDynamicId(), packet.getSlot(), packet.getItem());
            return;
        }

        player.getInventory().setSlot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }
}
