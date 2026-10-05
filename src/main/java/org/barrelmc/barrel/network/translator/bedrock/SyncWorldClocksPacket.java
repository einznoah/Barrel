package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.protocol.bedrock.data.clock.InitializeRegistryData;
import org.cloudburstmc.protocol.bedrock.data.clock.SyncStateData;
import org.cloudburstmc.protocol.bedrock.data.clock.SyncWorldClockStateData;
import org.cloudburstmc.protocol.bedrock.data.clock.SyncWorldClocksPayload;
import org.cloudburstmc.protocol.bedrock.data.clock.WorldClockData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import java.util.Collections;

// Servers send this instead of the SetTimePacket since 1.26.10
public class SyncWorldClocksPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        SyncWorldClocksPayload data = ((org.cloudburstmc.protocol.bedrock.packet.SyncWorldClocksPacket) pk).getData();

        if (data instanceof InitializeRegistryData) {
            for (WorldClockData clock : ((InitializeRegistryData) data).getClockData()) {
                if ("minecraft:overworld".equals(clock.getName())) {
                    player.setOverworldClockId(clock.getId());
                    this.sendTime(player, clock.getTime(), clock.isPaused());
                }
            }
        } else if (data instanceof SyncStateData) {
            for (SyncWorldClockStateData clock : ((SyncStateData) data).getClockData()) {
                if (clock.getClockId() == player.getOverworldClockId()) {
                    this.sendTime(player, clock.getTime(), clock.isPaused());
                }
            }
        }
    }

    private void sendTime(Player player, int time, boolean paused) {
        player.getJavaSession().send(new ClientboundSetTimePacket(0, Collections.singletonMap(ProxyServer.getInstance().getOverworldClockId(), new ClockNetworkState(time, 0, paused ? 0 : 1))));
    }
}
