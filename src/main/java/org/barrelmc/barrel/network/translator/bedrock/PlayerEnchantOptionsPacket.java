package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.ArrayList;

public class PlayerEnchantOptionsPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket) pk;

        player.getInventory().setEnchantOptions(new ArrayList<>(packet.getOptions()));
    }
}
