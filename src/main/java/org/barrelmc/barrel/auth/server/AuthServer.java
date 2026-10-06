/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.auth.server;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.Utils;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.HeightmapTypes;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;

import java.util.BitSet;
import java.util.Collections;

public class AuthServer extends SessionAdapter {

    // The client takes 0 for an entity that was not given an id yet, and does not join with it
    private static final int ENTITY_ID = 1;

    public AuthServer(Session session, String username) {
        session.send(new ClientboundLoginPacket(
                ENTITY_ID, false, new Key[]{Key.key("minecraft:overworld")},
                10, 6, 6, false, true, false,
                new PlayerSpawnInfo(
                        ProxyServer.getInstance().getOverworldId(), Key.key("minecraft:overworld"), 100,
                        GameMode.ADVENTURE, GameMode.ADVENTURE, false, false, null, 0, 63
                ),
                false, false
        ));

        this.generateWorld(session);

        session.send(new ClientboundSetDefaultSpawnPositionPacket(new GlobalPos(Key.key("minecraft:overworld"), Vector3i.from(8, 82, 8)), 0, 0));
        session.send(new ClientboundPlayerPositionPacket(0, 8, 82, 8, 0, 0, 0, 0, 0));
        session.send(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));

        session.send(new ClientboundSystemChatPacket(Component.text("§cPlease login with your Xbox account"), false));
        Thread loginThread = AuthManager.getInstance().getXboxLive().requestLiveToken(session, username);
        AuthManager.getInstance().getLoginThreads().put(username, loginThread);
    }

    private void generateWorld(Session session) {
        ChunkSection[] chunkSections = Utils.createChunkSections();

        // A cobblestone floor at y 80
        ChunkSection chunk = chunkSections[5 - ProxyServer.getInstance().getOverworldMinSection()];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, 0, z, 14);
            }
        }

        session.send(new ClientboundLevelChunkWithLightPacket(
                0, 0, Utils.writeChunkSections(chunkSections), Collections.singletonMap(HeightmapTypes.MOTION_BLOCKING, new long[37]),
                new BlockEntityInfo[0],
                new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), Collections.emptyList(), Collections.emptyList())
        ));
    }
}
