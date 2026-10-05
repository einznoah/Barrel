package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;

public class SetSpawnPositionPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket) pk;
        Vector3i pos = packet.getSpawnPosition();

        player.getJavaSession().send(new ClientboundSetDefaultSpawnPositionPacket(new GlobalPos(Key.key("minecraft:overworld"), pos), 0.0f, 0.0f));
    }
}
