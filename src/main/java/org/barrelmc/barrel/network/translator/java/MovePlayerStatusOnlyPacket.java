package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;

public class MovePlayerStatusOnlyPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundMovePlayerStatusOnlyPacket packet = (ServerboundMovePlayerStatusOnlyPacket) pk;

        player.getInput().setCollisions(packet.isOnGround(), packet.isHorizontalCollision());
    }
}
