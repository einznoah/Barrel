package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.math.Vector3;
import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.BedrockBlocks;
import org.barrelmc.barrel.player.Player;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

public class UseItemPacket implements JavaPacketTranslator {

    // How far a player reaches
    private static final float LIQUID_REACH = 5;

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundUseItemPacket packet = (ServerboundUseItemPacket) pk;

        player.getJavaSession().send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }

        // What a java client uses on water it uses without saying on which block: its server looks for the water
        // the player looks at. A bedrock client finds the water itself, and uses the item on it as on any block
        if (player.getInventory().isHeldItemUsedOnLiquid()) {
            Vector3 look = new Vector3();
            look.setRotation(packet.getYRot(), packet.getXRot());
            BedrockBlocks.LiquidHit liquid = player.getBedrockBlocks().findLiquid(player.getVector3f(), look.getDirectionVector(), LIQUID_REACH);
            if (liquid != null) {
                player.setOwnAction(liquid.position());
                player.getInventory().useItemOn(liquid.position(), liquid.face(), false, liquid.cursor());
                return;
            }
        }

        player.getInventory().useItem();
    }
}
