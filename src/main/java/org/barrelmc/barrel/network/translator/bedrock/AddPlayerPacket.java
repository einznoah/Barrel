package org.barrelmc.barrel.network.translator.bedrock;

import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.ItemConverter;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.utils.Utils;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

import java.util.EnumSet;

public class AddPlayerPacket implements BedrockPacketTranslator {

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket packet = (org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket) pk;

        Vector3f position = packet.getPosition();
        Vector3f rotation = packet.getRotation();
        GameProfile gameProfile = new GameProfile(packet.getUuid(), Utils.lengthCutter(packet.getUsername(), 16));
        CharSequence nameTag = packet.getMetadata().get(EntityDataTypes.NAME);

        Entity entity = new Entity(true);
        entity.setLocation(position.getX(), position.getY(), position.getZ(), rotation.getY(), rotation.getX());
        entity.setHeadYaw(rotation.getY());
        player.getEntities().put(packet.getRuntimeEntityId(), entity);

        player.getJavaSession().send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_GAME_MODE, PlayerListEntryAction.UPDATE_LISTED, PlayerListEntryAction.UPDATE_LATENCY, PlayerListEntryAction.UPDATE_DISPLAY_NAME), new PlayerListEntry[]{new PlayerListEntry(packet.getUuid(), gameProfile, true, 10, GameMode.SURVIVAL, Component.text(Utils.lengthCutter(nameTag == null ? null : nameTag.toString(), 16)), true, 0, null, 0L, null, null)}));
        player.getJavaSession().send(new ClientboundAddEntityPacket((int) packet.getRuntimeEntityId(), packet.getUuid(), EntityType.PLAYER, position.getX(), position.getY(), position.getZ(), rotation.getY(), rotation.getX(), rotation.getY()));
        if (!ItemConverter.isEmpty(packet.getHand())) {
            player.getJavaSession().send(new ClientboundSetEquipmentPacket((int) packet.getRuntimeEntityId(), new Equipment[]{new Equipment(EquipmentSlot.MAIN_HAND, ItemConverter.bedrockToJavaItem(packet.getHand()))}));
        }
    }
}
