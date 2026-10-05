package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class PlayerHotbarPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket) pk;

        if (packet.getContainerId() == ContainerId.INVENTORY && packet.getSelectedHotbarSlot() >= 0 && packet.getSelectedHotbarSlot() < 9) {
            player.getInventory().onBedrockHeldSlot(packet.getSelectedHotbarSlot());
        }
    }
}
