package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.utils.Utils;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

public class PlayerListPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket packet = (org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket) pk;
        ArrayList<PlayerListEntry> playerListEntries = new ArrayList<>();
        ArrayList<UUID> removedPlayers = new ArrayList<>();

        for (org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Entry entry : packet.getEntries()) {
            switch (entry.getAction()) {
                case ADD: {
                    GameProfile gameProfile = new GameProfile(entry.getUuid(), Utils.lengthCutter(entry.getName(), 16));
                    playerListEntries.add(new PlayerListEntry(entry.getUuid(), gameProfile, true, 0, GameMode.SURVIVAL, Component.text(Utils.lengthCutter(entry.getName(), 16)), true, 0, null, 0L, null, null));
                    break;
                }
                case REMOVE: {
                    removedPlayers.add(entry.getUuid());
                    break;
                }
            }
        }

        if (!playerListEntries.isEmpty()) {
            player.getJavaSession().send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_GAME_MODE, PlayerListEntryAction.UPDATE_LISTED, PlayerListEntryAction.UPDATE_LATENCY, PlayerListEntryAction.UPDATE_DISPLAY_NAME), playerListEntries.toArray(new PlayerListEntry[0])));
        }
        if (!removedPlayers.isEmpty()) {
            player.getJavaSession().send(new ClientboundPlayerInfoRemovePacket(removedPlayers));
        }
    }
}
