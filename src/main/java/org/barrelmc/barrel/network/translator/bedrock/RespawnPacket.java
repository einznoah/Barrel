package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;

public class RespawnPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.RespawnPacket packet = (org.cloudburstmc.protocol.bedrock.packet.RespawnPacket) pk;

        // The server found where the player comes back to life, after the java client asked to
        if (packet.getState() != org.cloudburstmc.protocol.bedrock.packet.RespawnPacket.State.SERVER_READY || player.getHealth() > 0) {
            return;
        }

        PlayerActionPacket playerActionPacket = new PlayerActionPacket();
        playerActionPacket.setAction(PlayerActionType.RESPAWN);
        playerActionPacket.setBlockPosition(Vector3i.ZERO);
        playerActionPacket.setResultPosition(Vector3i.ZERO);
        playerActionPacket.setFace(-1);
        playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
        player.getBedrockSession().sendPacket(playerActionPacket);

        // A java client makes a new player of the one that died, which has nothing of what the server did not send again
        GameMode gameMode = TranslatorUtils.translateGamemodeToJE(player.getGameMode());
        player.getJavaSession().send(new ClientboundRespawnPacket(new PlayerSpawnInfo(ProxyServer.getInstance().getOverworldId(), Key.key("minecraft:overworld"), 100, gameMode, gameMode, false, false, null, 0, 63), false, false));
        player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
        player.getEffects().clear();
        player.getInventory().sendContents();
        player.getJavaSession().send(new ClientboundSetHeldSlotPacket(player.getInventory().getHeldSlot()));
        player.getJavaSession().send(new ClientboundSetExperiencePacket(player.getExperienceProgress(), player.getExperienceLevel(), 0));
    }
}
