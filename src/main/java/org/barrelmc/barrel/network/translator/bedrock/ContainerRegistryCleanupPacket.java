package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// The server no longer keeps what was in some bundles, they are gone or out of reach
public class ContainerRegistryCleanupPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ContainerRegistryCleanupPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ContainerRegistryCleanupPacket) pk;

        for (FullContainerName container : packet.getContainers()) {
            if (container.getDynamicId() != null) {
                player.getInventory().removeBundle(container.getDynamicId());
            }
        }
    }
}
