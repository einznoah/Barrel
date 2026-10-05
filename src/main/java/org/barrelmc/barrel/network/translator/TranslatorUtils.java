/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.translator;

import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

public class TranslatorUtils {

    public static GameMode translateGamemodeToJE(GameType gameType) {
        String gameTypeString = gameType.toString();

        if (gameTypeString.contains("VIEWER")) {
            return GameMode.SPECTATOR;
        }

        return GameMode.valueOf(gameTypeString);
    }
}
