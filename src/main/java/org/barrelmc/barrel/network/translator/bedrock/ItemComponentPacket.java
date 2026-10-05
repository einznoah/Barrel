package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public class ItemComponentPacket implements BedrockPacketTranslator {

    // The items of the packets that follow can only be read with the names this packet gives their ids
    @Override
    public boolean immediate() {
        return true;
    }

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket) pk;

        for (ItemDefinition itemDefinition : packet.getItems()) {
            player.getItemDefinitions().put(itemDefinition.getRuntimeId(), itemDefinition);
        }
    }
}
