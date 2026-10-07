package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

public class PlayerActionPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundPlayerActionPacket playerActionPacket = (ServerboundPlayerActionPacket) pk;
        PlayerBlockActionData blockActionData;
        Vector3i blockPos = Vector3i.from(playerActionPacket.getPosition().getX(), playerActionPacket.getPosition().getY(), playerActionPacket.getPosition().getZ());

        PlayerAction action = playerActionPacket.getAction();
        if (action == PlayerAction.START_DIGGING || action == PlayerAction.FINISH_DIGGING) {
            player.setOwnAction(blockPos);
        }
        if (action == PlayerAction.START_DIGGING && player.getGameMode() == GameType.CREATIVE) {
            action = PlayerAction.FINISH_DIGGING;
        }
        switch (action) {
            case START_DIGGING:
            case CANCEL_DIGGING:
            case FINISH_DIGGING:
                // The client keeps the blocks it expects to have changed until the server acknowledged its action
                player.getJavaSession().send(new ClientboundBlockChangedAckPacket(playerActionPacket.getSequence()));
                break;
        }
        switch (action) {
            case START_DIGGING:
                if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                    // TODO: implement for client authoritative
                } else {
                    player.getPlayerAuthInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
                    blockActionData = new PlayerBlockActionData();
                    blockActionData.setAction(PlayerActionType.START_BREAK);
                    blockActionData.setBlockPosition(blockPos);
                    blockActionData.setFace(playerActionPacket.getFace().ordinal());
                    player.getPlayerAuthInputActions().add(blockActionData);
                    player.setDiggingPosition(blockPos);
                    player.setDiggingFace(playerActionPacket.getFace());
                    player.setDiggingStatus(PlayerActionType.START_BREAK);
                }
                break;
            case CANCEL_DIGGING:
                if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                    // TODO: implement for client authoritative
                } else {
                    player.getPlayerAuthInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
                    player.setDiggingStatus(PlayerActionType.ABORT_BREAK);
                    blockActionData = new PlayerBlockActionData();
                    blockActionData.setAction(PlayerActionType.ABORT_BREAK);
                    blockActionData.setBlockPosition(blockPos);
                    blockActionData.setFace(playerActionPacket.getFace().ordinal());
                    player.getPlayerAuthInputActions().add(blockActionData);
                }
                break;
            case FINISH_DIGGING:
                if (player.getStartGamePacketCache().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                    // TODO: implement for client authoritative
                } else {
                    // perform break block action
                    player.getPlayerAuthInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
                    if (player.getGameMode() == GameType.CREATIVE) {
                        // Creative players break instantly, the server has not been told that they started yet
                        blockActionData = new PlayerBlockActionData();
                        blockActionData.setAction(PlayerActionType.START_BREAK);
                        blockActionData.setBlockPosition(blockPos);
                        blockActionData.setFace(playerActionPacket.getFace().ordinal());
                        player.getPlayerAuthInputActions().add(blockActionData);
                    }
                    player.setDiggingStatus(PlayerActionType.BLOCK_PREDICT_DESTROY);
                    boolean serverBreaksBlocks = player.getStartGamePacketCache().isServerAuthoritativeBlockBreaking();
                    if (serverBreaksBlocks) {
                        // A server that breaks the block itself is told that the player went on to the end
                        blockActionData = new PlayerBlockActionData();
                        blockActionData.setAction(PlayerActionType.BLOCK_CONTINUE_DESTROY);
                        blockActionData.setBlockPosition(blockPos);
                        blockActionData.setFace(playerActionPacket.getFace().ordinal());
                        player.getPlayerAuthInputActions().add(blockActionData);
                    }
                    blockActionData = new PlayerBlockActionData();
                    blockActionData.setAction(PlayerActionType.BLOCK_PREDICT_DESTROY);
                    blockActionData.setBlockPosition(blockPos);
                    blockActionData.setFace(playerActionPacket.getFace().ordinal());
                    player.getPlayerAuthInputActions().add(blockActionData);
                    if (serverBreaksBlocks) {
                        // And that it is not breaking a block anymore
                        blockActionData = new PlayerBlockActionData();
                        blockActionData.setAction(PlayerActionType.ABORT_BREAK);
                        blockActionData.setBlockPosition(blockPos);
                        blockActionData.setFace(0);
                        player.getPlayerAuthInputActions().add(blockActionData);
                    }
                }
                break;
            case DROP_ITEM:
            case DROP_ITEM_STACK:
                player.getInventory().dropItem(player.getInventory().getHeldItemSlot(), action == PlayerAction.DROP_ITEM_STACK);
                player.getInventory().sendSlot(player.getInventory().getHeldItemSlot());
                break;
            case RELEASE_USE_ITEM:
                player.getInventory().releaseItem();
                break;
            case SWAP_HANDS:
                player.getInventory().swapSlots(player.getInventory().getHeldItemSlot(), player.getInventory().getOffhandSlot());
                player.getInventory().sendSlot(player.getInventory().getHeldItemSlot());
                player.getInventory().sendSlot(player.getInventory().getOffhandSlot());
                break;
        }
    }
}
