package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

public class AcceptTeleportationPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        // The java client is where it was put: what it sends of where the player is from here on is sent from there
        player.getInput().acceptTeleport(((ServerboundAcceptTeleportationPacket) pk).getId());
    }
}
