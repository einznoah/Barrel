package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

public class ClientCommandPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundClientCommandPacket packet = (ServerboundClientCommandPacket) pk;

        if (packet.getRequest() == ClientCommand.PERFORM_RESPAWN) {
            RespawnPacket respawnPacket = new RespawnPacket();

            respawnPacket.setPosition(Vector3f.from(0, 0, 0));
            respawnPacket.setRuntimeEntityId(player.getRuntimeEntityId());
            respawnPacket.setState(RespawnPacket.State.CLIENT_READY);

            player.getBedrockSession().sendPacket(respawnPacket);
        }
    }
}
