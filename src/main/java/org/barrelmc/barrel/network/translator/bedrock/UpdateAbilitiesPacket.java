package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;

import java.util.Set;

public class UpdateAbilitiesPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket packet = (org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket) pk;

        if (packet.getUniqueEntityId() == player.getUniqueEntityId()) {
            for (AbilityLayer abilityLayer : packet.getAbilityLayers().toArray(new AbilityLayer[0])) {
                if (abilityLayer.getLayerType() == AbilityLayer.Type.BASE) {
                    Set<Ability> abilityValues = abilityLayer.getAbilityValues();
                    if (abilityValues.contains(Ability.NO_CLIP) && player.getGameMode() == GameType.CREATIVE) {
                        player.setGameMode(GameType.SURVIVAL_VIEWER);
                        player.getJavaSession().send(new ClientboundGameEventPacket(GameEvent.CHANGE_GAME_MODE, TranslatorUtils.translateGamemodeToJE(GameType.SURVIVAL_VIEWER)));
                    }
                    player.getJavaSession().send(new ClientboundPlayerAbilitiesPacket(abilityValues.contains(Ability.INVULNERABLE), abilityValues.contains(Ability.MAY_FLY), abilityValues.contains(Ability.FLYING), abilityValues.contains(Ability.INSTABUILD), 0.05f, 0.1f));
                }
            }
        }
    }
}
