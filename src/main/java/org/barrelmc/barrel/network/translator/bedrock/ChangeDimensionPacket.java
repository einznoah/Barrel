package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundLoadingScreenPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
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
        player.getRiding().clear();
        player.getEntityRuntimeIds().clear();

        // A bedrock client shows a loading screen from here on. The server sends the chunks and then tells that it is
        // done, which is when the screen closes. Until then the player stays where the server put it
        player.setChangingDimension(true);
        player.setDimensionAnswerOwed(true);
        player.setDimensionLoadingScreen(packet.getLoadingScreenId());
        player.getInput().startDimensionChange();
        // The server only awaits a loading screen it gave a number
        if (packet.getLoadingScreenId() != null) {
            ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
            loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.START_LOADING_SCREEN);
            loadingScreenPacket.setLoadingScreenId(packet.getLoadingScreenId());
            player.getBedrockSession().sendPacket(loadingScreenPacket);
        }

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
        player.getInput().teleportJava();

        // A player that died is brought to where it comes back to life: its health is told once it has some again,
        // told before that the java client would show that it died once more
        if (player.getHealth() > 0) {
            player.getJavaSession().send(new ClientboundSetHealthPacket(player.getHealth(), player.getFood(), player.getSaturation()));
        }
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

        // Without a loading screen there is nothing the server tells the end of: that is how it brings a player that
        // died to another dimension. What it tells all the same is answered when it comes
        if (packet.getLoadingScreenId() == null) {
            finish(player, false);
        }
    }

    // What a bedrock client tells the server once it is in the dimension, which is what the server told it before
    public static void answer(Player player) {
        if (!player.isDimensionAnswerOwed()) {
            return;
        }
        player.setDimensionAnswerOwed(false);

        PlayerActionPacket playerActionPacket = new PlayerActionPacket();
        playerActionPacket.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
        playerActionPacket.setBlockPosition(Vector3i.ZERO);
        playerActionPacket.setResultPosition(Vector3i.ZERO);
        playerActionPacket.setFace(0);
        playerActionPacket.setRuntimeEntityId(player.getRuntimeEntityId());
        player.getBedrockSession().sendPacket(playerActionPacket);
    }

    // The player is in the dimension: the loading screen of a bedrock client closes, a java client waits to be told
    // that the chunks come. The server is answered when it told that the player has arrived, or was waited for
    public static void finish(Player player, boolean answer) {
        if (!player.isChangingDimension()) {
            return;
        }
        player.setChangingDimension(false);

        if (answer) {
            answer(player);
        }
        if (player.getDimensionLoadingScreen() != null) {
            ServerboundLoadingScreenPacket loadingScreenPacket = new ServerboundLoadingScreenPacket();
            loadingScreenPacket.setType(ServerboundLoadingScreenPacketType.END_LOADING_SCREEN);
            loadingScreenPacket.setLoadingScreenId(player.getDimensionLoadingScreen());
            player.getBedrockSession().sendPacket(loadingScreenPacket);
        }

        player.getInput().teleportJava();
        player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
    }
}
