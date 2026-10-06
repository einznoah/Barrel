package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

public class MobEquipmentPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket) pk;

        // What the player itself holds comes with its inventory
        if (player.getEntities().containsKey(packet.getRuntimeEntityId())) {
            EquipmentSlot slot = packet.getContainerId() == ContainerId.OFFHAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.MAIN_HAND;
            player.getJavaSession().send(new ClientboundSetEquipmentPacket((int) packet.getRuntimeEntityId(), new Equipment[]{new Equipment(slot, ItemConverter.bedrockToJavaItem(packet.getItem()))}));
        }
    }
}
