package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

public class MobArmorEquipmentPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket packet = (org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket) pk;

        if (player.getEntities().containsKey(packet.getRuntimeEntityId())) {
            player.getJavaSession().send(new ClientboundSetEquipmentPacket((int) packet.getRuntimeEntityId(), new Equipment[]{
                    new Equipment(EquipmentSlot.HELMET, ItemConverter.bedrockToJavaItem(packet.getHelmet())),
                    new Equipment(EquipmentSlot.CHESTPLATE, ItemConverter.bedrockToJavaItem(packet.getChestplate())),
                    new Equipment(EquipmentSlot.LEGGINGS, ItemConverter.bedrockToJavaItem(packet.getLeggings())),
                    new Equipment(EquipmentSlot.BOOTS, ItemConverter.bedrockToJavaItem(packet.getBoots()))
            }));
        }
    }
}
