package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;

public class StartGamePacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.StartGamePacket packet = (org.cloudburstmc.protocol.bedrock.packet.StartGamePacket) pk;

        player.setRuntimeEntityId(packet.getRuntimeEntityId());
        player.setUniqueEntityId(packet.getUniqueEntityId());
        player.setOldPosition(packet.getPlayerPosition());
        player.setLastServerPosition(packet.getPlayerPosition());
        player.setLastServerRotation(packet.getRotation());
        player.setStartGamePacketCache(packet);
        player.setLevelGameMode(packet.getLevelGameType());
        player.setGameMode(packet.getPlayerGameType());

        ClientboundLoginPacket serverJoinGamePacket = new ClientboundLoginPacket(
                (int) packet.getRuntimeEntityId(), false,
                new Key[]{Key.key("minecraft:overworld"), Key.key("minecraft:the_nether"), Key.key("minecraft:the_end")},
                10, 16, 16, false, true, false,
                new PlayerSpawnInfo(
                        ProxyServer.getInstance().getOverworldId(), Key.key("minecraft:overworld"), 100,
                        TranslatorUtils.translateGamemodeToJE(player.getGameMode()),
                        TranslatorUtils.translateGamemodeToJE(player.getGameMode()),
                        false, false, null, 0, 63
                ),
                false, false
        );
        player.getJavaSession().send(serverJoinGamePacket);
        // The server sends where the eyes of the player are
        Vector3f position = packet.getPlayerPosition().sub(0, Entity.PLAYER_EYE_HEIGHT, 0);
        // The client has no world to center on before it received the login packet
        player.setPosition(position);
        Vector2f rotation = packet.getRotation();
        ClientboundPlayerPositionPacket serverPlayerPositionRotationPacket = new ClientboundPlayerPositionPacket(0, position.getX(), position.getY(), position.getZ(), 0, 0, 0, rotation.getY(), rotation.getX());
        player.getJavaSession().send(serverPlayerPositionRotationPacket);
        player.getJavaSession().send(new ClientboundEntityEventPacket((int) packet.getRuntimeEntityId(), EntityEvent.PLAYER_SET_NO_PERMISSIONS));
        // The client stays on the loading screen until it is told that the chunks are coming
        player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));

        // The client already sent its render distance before it joined the game
        RequestChunkRadiusPacket chunkRadiusPacket = new RequestChunkRadiusPacket();
        chunkRadiusPacket.setRadius(player.getRenderDistance());
        chunkRadiusPacket.setMaxRadius(player.getRenderDistance());
        player.getBedrockSession().sendPacket(chunkRadiusPacket);
    }
}
