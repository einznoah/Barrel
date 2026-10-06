package org.barrelmc.barrel.network.translator.java;

import org.barrelmc.barrel.network.translator.interfaces.JavaPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;

public class PlayerAbilitiesPacket implements JavaPacketTranslator {

    @Override
    public void translate(MinecraftPacket pk, Player player) {
        ServerboundPlayerAbilitiesPacket packet = (ServerboundPlayerAbilitiesPacket) pk;

        if (player.getStartGamePacketCache().getAuthoritativeMovementMode() != AuthoritativeMovementMode.CLIENT) {
            // A server that moves the player itself is told with what the player does
            if (packet.isFlying() != player.isFlying()) {
                player.setFlying(packet.isFlying());
                player.getPlayerAuthInputData().add(packet.isFlying() ? PlayerAuthInputData.START_FLYING : PlayerAuthInputData.STOP_FLYING);
            }
            return;
        }

        RequestAbilityPacket requestAbilityPacket = new RequestAbilityPacket();
        requestAbilityPacket.setAbility(Ability.FLYING);
        requestAbilityPacket.setType(Ability.Type.BOOLEAN);
        requestAbilityPacket.setBoolValue(packet.isFlying());
        requestAbilityPacket.setFloatValue(0.0f);

        player.getBedrockSession().sendPacket(requestAbilityPacket);
    }
}
