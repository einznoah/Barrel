package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class InventoryContentPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket) pk;

        FullContainerName container = packet.getContainerNameData();
        if (container != null && container.getContainer() == ContainerSlotType.DYNAMIC_CONTAINER && container.getDynamicId() != null) {
            // What is in a bundle, which is sent apart from the bundle
            player.getInventory().setBundleContents(container.getDynamicId(), packet.getContents());
            return;
        }

        player.getInventory().setContents(packet.getContainerId(), packet.getContents());
    }
}
