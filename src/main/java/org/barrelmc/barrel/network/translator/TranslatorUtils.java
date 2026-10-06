/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.translator;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TranslatorUtils {

    private static final double PLAYER_MOVEMENT_SPEED = 0.1F;

    public static GameMode translateGamemodeToJE(GameType gameType) {
        String gameTypeString = gameType.toString();

        if (gameTypeString.contains("VIEWER")) {
            return GameMode.SPECTATOR;
        }

        return GameMode.valueOf(gameTypeString);
    }

    // A java client is told how the speed of the player is made up. That of a bedrock client is a number that has
    // sprinting in it, so it is made up again from the effects the player has
    public static void sendMovementSpeed(Player player) {
        List<AttributeModifier> modifiers = new ArrayList<>();
        Integer speed = player.getEffects().get(Effect.SPEED);
        if (speed != null) {
            modifiers.add(new AttributeModifier(Key.key("effect.speed"), 0.2 * (speed + 1), ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }
        Integer slowness = player.getEffects().get(Effect.SLOWNESS);
        if (slowness != null) {
            modifiers.add(new AttributeModifier(Key.key("effect.slowness"), -0.15 * (slowness + 1), ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }
        if (player.isSprinting()) {
            // The client takes this off itself when it stops sprinting
            modifiers.add(new AttributeModifier(Key.key("sprinting"), 0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }

        Attribute movementSpeed = new Attribute(AttributeType.Builtin.MOVEMENT_SPEED, PLAYER_MOVEMENT_SPEED, modifiers);
        player.getJavaSession().send(new ClientboundUpdateAttributesPacket((int) player.getRuntimeEntityId(), Collections.singletonList(movementSpeed)));
    }

    public static void sendEntityPosition(Player player, long runtimeEntityId, Entity entity, boolean onGround) {
        player.getJavaSession().send(new ClientboundTeleportEntityPacket((int) runtimeEntityId, Vector3d.from(entity.x, entity.y, entity.z), Vector3d.ZERO, entity.yaw, entity.pitch, Collections.emptyList(), onGround));
        player.getJavaSession().send(new ClientboundRotateHeadPacket((int) runtimeEntityId, entity.getHeadYaw()));
    }
}
