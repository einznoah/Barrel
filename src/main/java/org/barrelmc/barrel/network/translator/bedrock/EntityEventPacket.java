package org.barrelmc.barrel.network.translator.bedrock;

import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundHurtAnimationPacket;

import java.util.EnumMap;
import java.util.Map;

// An entity does something that is seen for a moment: it is hurt, it swings at something, hearts rise from it
public class EntityEventPacket implements BedrockPacketTranslator {

    // What a java client is told for what a bedrock server tells. What is not here a java client has nothing for,
    // or shows by itself
    private static final Map<EntityEventType, EntityEvent> JAVA_EVENTS = new EnumMap<>(EntityEventType.class);

    static {
        JAVA_EVENTS.put(EntityEventType.ATTACK_START, EntityEvent.ATTACK);
        JAVA_EVENTS.put(EntityEventType.ATTACK_STOP, EntityEvent.STOP_ATTACK);
        JAVA_EVENTS.put(EntityEventType.TAME_FAILED, EntityEvent.TAMEABLE_TAMING_FAILED);
        JAVA_EVENTS.put(EntityEventType.TAME_SUCCEEDED, EntityEvent.TAMEABLE_TAMING_SUCCEEDED);
        JAVA_EVENTS.put(EntityEventType.SHAKE_WETNESS, EntityEvent.WOLF_SHAKE_WATER);
        JAVA_EVENTS.put(EntityEventType.SHAKE_WETNESS_STOP, EntityEvent.WOLF_SHAKE_WATER_STOP);
        JAVA_EVENTS.put(EntityEventType.EAT_GRASS, EntityEvent.SHEEP_GRAZE_OR_TNT_CART_EXPLODE);
        JAVA_EVENTS.put(EntityEventType.GOLEM_FLOWER_OFFER, EntityEvent.IRON_GOLEM_HOLD_POPPY);
        JAVA_EVENTS.put(EntityEventType.GOLEM_FLOWER_WITHDRAW, EntityEvent.IRON_GOLEM_EMPTY_HAND);
        JAVA_EVENTS.put(EntityEventType.VILLAGER_ANGRY, EntityEvent.VILLAGER_ANGRY);
        JAVA_EVENTS.put(EntityEventType.VILLAGER_HAPPY, EntityEvent.VILLAGER_HAPPY);
        JAVA_EVENTS.put(EntityEventType.LOVE_PARTICLES, EntityEvent.ANIMAL_EMIT_HEARTS);
        JAVA_EVENTS.put(EntityEventType.IN_LOVE_HEARTS, EntityEvent.ANIMAL_EMIT_HEARTS);
        JAVA_EVENTS.put(EntityEventType.WITCH_HAT_MAGIC, EntityEvent.WITCH_EMIT_PARTICLES);
        JAVA_EVENTS.put(EntityEventType.ZOMBIE_VILLAGER_CURE, EntityEvent.ZOMBIE_VILLAGER_CURE);
        JAVA_EVENTS.put(EntityEventType.FIREWORK_EXPLODE, EntityEvent.FIREWORK_EXPLODE);
        JAVA_EVENTS.put(EntityEventType.SQUID_FLEEING, EntityEvent.SQUID_RESET_ROTATION);
        JAVA_EVENTS.put(EntityEventType.GUARDIAN_ATTACK_ANIMATION, EntityEvent.GUARDIAN_MAKE_SOUND);
        JAVA_EVENTS.put(EntityEventType.DEATH_SMOKE_CLOUD, EntityEvent.MOB_EMIT_SMOKE);
        JAVA_EVENTS.put(EntityEventType.CONSUME_TOTEM, EntityEvent.TOTEM_OF_UNDYING_MAKE_SOUND);
    }

    @Override
    public void translate(BedrockPacket pk, Player player) {
        org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket packet = (org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket) pk;

        Entity entity = player.getEntities().get(packet.getRuntimeEntityId());
        boolean self = packet.getRuntimeEntityId() == player.getRuntimeEntityId();
        if (entity == null && !self) {
            return;
        }

        switch (packet.getType()) {
            case HURT: {
                player.getJavaSession().send(new ClientboundHurtAnimationPacket((int) packet.getRuntimeEntityId(), 0));
                break;
            }
            case DEATH: {
                // TODO: The health of the player, it does not see itself die
                if (!self) {
                    player.getJavaSession().send(new ClientboundEntityEventPacket((int) packet.getRuntimeEntityId(), EntityEvent.LIVING_DEATH));
                }
                break;
            }
            case JUMP: {
                // Only the hop of a rabbit is something a java client is told
                if (!self && entity.getType() == EntityType.RABBIT) {
                    player.getJavaSession().send(new ClientboundEntityEventPacket((int) packet.getRuntimeEntityId(), EntityEvent.RABBIT_JUMP_OR_MINECART_SPAWNER_DELAY_RESET));
                }
                break;
            }
            default: {
                EntityEvent javaEvent = JAVA_EVENTS.get(packet.getType());
                // What stands in for an entity a java client has no kind for does none of this
                if (javaEvent != null && (self || !entity.isStandIn())) {
                    player.getJavaSession().send(new ClientboundEntityEventPacket((int) packet.getRuntimeEntityId(), javaEvent));
                }
                break;
            }
        }
    }
}
