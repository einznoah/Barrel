package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

// The server takes the player to another dimension, through a portal or to where it comes back to life
public class ChangeDimensionPacket implements BedrockPacketTranslator {

    private static final int TICK_MILLIS = 50;

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket packet = (org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket) pk;

        // What was known of the dimension the player leaves is not true where it comes to
        player.setDimension(packet.getDimension());
        player.getBedrockBlocks().clear();
        player.getSubChunkRequests().clear();
        player.getEntities().clear();
        player.getEntityRuntimeIds().clear();

        // A bedrock client shows a loading screen from here on. The server sends the chunks and then tells that it is
        // done, which is when the screen closes
        player.setChangingDimension(true);
        player.setDimensionLoadingScreen(packet.getLoadingScreenId());
        ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
        loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN);
        loadingScreenPacket.setLoadingScreenId(packet.getLoadingScreenId());
        player.getBedrockSession().sendPacket(loadingScreenPacket);

        // A java client comes into another dimension as a player that comes back to life: it makes a new world and a
        // new player, which keeps what its entity was told and nothing else
        ProxyServer.Dimension dimension = player.getJavaDimension();
        GameMode gameMode = TranslatorUtils.translateGamemodeToJE(player.getGameMode());
        player.getJavaSession().send(new ClientboundRespawnPacket(new PlayerSpawnInfo(dimension.id(), dimension.name(), 100, gameMode, gameMode, false, false, null, 0, 63), true, true));

        // Unlike elsewhere, the server sends where the feet of the player are
        Vector3f position = packet.getPosition();
        player.setPosition(position);
        player.getInput().setTeleported();
        player.getJavaSession().send(new ClientboundSetChunkCacheCenterPacket(position.getFloorX() >> 4, position.getFloorZ() >> 4));
        sendPosition(player);

        player.getJavaSession().send(new ClientboundSetHealthPacket(player.getHealth(), player.getFood(), player.getSaturation()));
        player.getJavaSession().send(new ClientboundSetExperiencePacket(player.getExperienceProgress(), player.getExperienceLevel(), 0));
        if (player.getAbilities() != null) {
            player.getJavaSession().send(player.getAbilities());
        }
        long now = System.currentTimeMillis();
        for (Player.SentEffect sent : player.getSentEffects().values()) {
            ClientboundUpdateMobEffectPacket effect = sent.packet();
            // An effect that lasts for ever is told as one of less than no time
            int left = effect.getDuration() < 0 ? effect.getDuration() : (int) Math.max(1, effect.getDuration() - (now - sent.time()) / TICK_MILLIS);
            player.getJavaSession().send(new ClientboundUpdateMobEffectPacket(effect.getEntityId(), effect.getEffect(), effect.getAmplifier(), left, effect.isAmbient(), effect.isShowParticles(), effect.isShowIcon(), effect.isBlend()));
        }
        TranslatorUtils.sendMovementSpeed(player);
        player.getInventory().sendContents();
        player.getJavaSession().send(new ClientboundSetHeldSlotPacket(player.getInventory().getHeldSlot()));
    }

    // Where the player is and where it looks, the client is put there whatever it believes
    public static void sendPosition(Player player) {
        player.getJavaSession().send(new ClientboundPlayerPositionPacket(0, player.x, player.y, player.z, 0, 0, 0, player.getYaw(), player.getPitch()));
    }
}
