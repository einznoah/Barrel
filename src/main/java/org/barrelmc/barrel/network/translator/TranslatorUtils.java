/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.translator;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import java.util.Collections;

public class TranslatorUtils {

    public static GameMode translateGamemodeToJE(GameType gameType) {
        String gameTypeString = gameType.toString();

        if (gameTypeString.contains("VIEWER")) {
            return GameMode.SPECTATOR;
        }

        return GameMode.valueOf(gameTypeString);
    }

    public static void sendEntityPosition(Player player, long runtimeEntityId, Entity entity, boolean onGround) {
        player.getJavaSession().send(new ClientboundTeleportEntityPacket((int) runtimeEntityId, Vector3d.from(entity.x, entity.y, entity.z), Vector3d.ZERO, entity.yaw, entity.pitch, Collections.emptyList(), onGround));
        player.getJavaSession().send(new ClientboundRotateHeadPacket((int) runtimeEntityId, entity.getHeadYaw()));
    }
}
