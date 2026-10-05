package org.barrelmc.barrel.network.translator.interfaces;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.barrelmc.barrel.player.Player;

public interface JavaPacketTranslator {

    void translate(MinecraftPacket pk, Player player);
}
