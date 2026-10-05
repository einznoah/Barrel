package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.AdvancementTabAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSelectAdvancementsTabPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSeenAdvancementsPacket;

public class SeenAdvancementsPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundSeenAdvancementsPacket packet = (ServerboundSeenAdvancementsPacket) pk;

        if (packet.getAction() == AdvancementTabAction.OPENED_TAB) {
            player.getJavaSession().send(new ClientboundSelectAdvancementsTabPacket(packet.getTabId()));
        }
    }
}
