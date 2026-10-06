package org.barrelmc.barrel.network.converter;

import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ColorParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// What an entity is like is numbered by the kind of entity on java. A java client disconnects if it is sent something
// under a number its kind of entity has something else at, so only what is known to be right is translated
public class EntityDataConverter {

    // What every java entity has
    private static final int JAVA_FLAGS = 0;
    private static final int JAVA_CUSTOM_NAME = 2;
    private static final int JAVA_CUSTOM_NAME_VISIBLE = 3;
    private static final int JAVA_POSE = 6;
    private static final int JAVA_ON_FIRE = 0x01;
    private static final int JAVA_SNEAKING = 0x02;
    private static final int JAVA_SPRINTING = 0x08;
    private static final int JAVA_SWIMMING = 0x10;
    private static final int JAVA_INVISIBLE = 0x20;
    private static final int JAVA_GLIDING = 0x80;

    private static final int JAVA_EFFECT_PARTICLES = 10;
    private static final int JAVA_BABY = 16;
    private static final int JAVA_CLOUD_RADIUS = 8;
    private static final int JAVA_CLOUD_PARTICLE = 10;
    // After whether it is a baby and whether it stays one, which every animal has
    private static final int JAVA_SHEEP_WOOL = 18;
    private static final int JAVA_SHEEP_SHEARED = 0x10;
    private static final int JAVA_CREEPER_SWELLING = 16;
    private static final int JAVA_CREEPER_CHARGED = 17;
    private static final int JAVA_CREEPER_IGNITED = 18;

    // The entities java keeps whether they are a baby at the same place for
    private static final Set<EntityType> JAVA_AGEABLE_ENTITIES = EnumSet.of(
            EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE, EntityType.DONKEY,
            EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.WOLF, EntityType.CAT, EntityType.OCELOT, EntityType.RABBIT,
            EntityType.FOX, EntityType.PANDA, EntityType.POLAR_BEAR, EntityType.BEE, EntityType.TURTLE, EntityType.GOAT, EntityType.AXOLOTL,
            EntityType.STRIDER, EntityType.HOGLIN, EntityType.CAMEL, EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.VILLAGER,
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOGLIN
    );

    // The effects a bedrock server lists as visible are packed in a number, each takes this many bits
    private static final int VISIBLE_EFFECT_BITS = 7;

    // The player is given for the entity that is the player of the java client itself
    public static EntityMetadata<?, ?>[] bedrockToJavaEntityData(Entity entity, EntityDataMap entityData, Player self) {
        List<EntityMetadata<?, ?>> javaEntityData = new ArrayList<>();
        EntityType entityType = entity.getType();
        boolean flags = entityData.getFlags() != null;

        if (flags) {
            int javaFlags = (entityData.getFlag(EntityFlag.ON_FIRE) ? JAVA_ON_FIRE : 0) | (entityData.getFlag(EntityFlag.INVISIBLE) ? JAVA_INVISIBLE : 0)
                    | (entityData.getFlag(EntityFlag.SWIMMING) ? JAVA_SWIMMING : 0) | (entityData.getFlag(EntityFlag.GLIDING) ? JAVA_GLIDING : 0);
            if (self == null) {
                javaFlags |= (entityData.getFlag(EntityFlag.SNEAKING) ? JAVA_SNEAKING : 0) | (entityData.getFlag(EntityFlag.SPRINTING) ? JAVA_SPRINTING : 0);
                javaEntityData.add(new ByteEntityMetadata(JAVA_FLAGS, MetadataTypes.BYTE, (byte) javaFlags));
                if (entityType == EntityType.PLAYER) {
                    // A java client goes by this for how a player stands
                    Pose pose = entityData.getFlag(EntityFlag.GLIDING) ? Pose.FALL_FLYING : entityData.getFlag(EntityFlag.SWIMMING) ? Pose.SWIMMING : entityData.getFlag(EntityFlag.SNEAKING) ? Pose.SNEAKING : Pose.STANDING;
                    javaEntityData.add(new ObjectEntityMetadata<>(JAVA_POSE, MetadataTypes.POSE, pose));
                }
            } else if ((javaFlags & (JAVA_ON_FIRE | JAVA_INVISIBLE)) != entity.getOwnFlags()) {
                // The client knows itself whether its player sneaks or sprints, and stops if it is told what the
                // server knew a moment ago. It is only told when what it can not know changed
                entity.setOwnFlags(javaFlags & (JAVA_ON_FIRE | JAVA_INVISIBLE));
                javaFlags |= (self.isSneaking() ? JAVA_SNEAKING : 0) | (self.isSprinting() ? JAVA_SPRINTING : 0);
                javaEntityData.add(new ByteEntityMetadata(JAVA_FLAGS, MetadataTypes.BYTE, (byte) javaFlags));
            }
        }

        if (entityType != EntityType.PLAYER) {
            // The name a mob was given, the one of a player is on its profile
            if (entityData.containsKey(EntityDataTypes.NAME)) {
                String name = entityData.get(EntityDataTypes.NAME).toString();
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_CUSTOM_NAME, MetadataTypes.OPTIONAL_COMPONENT, name.isEmpty() ? Optional.<Component>empty() : Optional.<Component>of(Component.text(name))));
            }
            if (flags) {
                javaEntityData.add(new BooleanEntityMetadata(JAVA_CUSTOM_NAME_VISIBLE, MetadataTypes.BOOLEAN, entityData.getFlag(EntityFlag.ALWAYS_SHOW_NAME)));
            }
        }

        if (entityType == EntityType.AREA_EFFECT_CLOUD) {
            // What a lingering potion leaves behind
            if (entityData.containsKey(EntityDataTypes.AREA_EFFECT_CLOUD_RADIUS)) {
                javaEntityData.add(new FloatEntityMetadata(JAVA_CLOUD_RADIUS, MetadataTypes.FLOAT, entityData.get(EntityDataTypes.AREA_EFFECT_CLOUD_RADIUS)));
            }
            if (entityData.containsKey(EntityDataTypes.EFFECT_COLOR)) {
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_CLOUD_PARTICLE, MetadataTypes.PARTICLE, getEffectParticle(entityData.get(EntityDataTypes.EFFECT_COLOR))));
            }
        } else if (entityData.containsKey(EntityDataTypes.VISIBLE_MOB_EFFECTS)) {
            // The swirls of the effects a player or a mob has
            List<Particle> particles = new ArrayList<>();
            for (long visibleEffects = entityData.get(EntityDataTypes.VISIBLE_MOB_EFFECTS); visibleEffects != 0; visibleEffects >>>= VISIBLE_EFFECT_BITS) {
                // The last bit tells whether the effect is one of a beacon
                int color = PotionConverter.getEffectColor((int) (visibleEffects & ((1 << VISIBLE_EFFECT_BITS) - 1)) >> 1);
                if (color != -1) {
                    particles.add(getEffectParticle(color));
                }
            }
            // Only an entity that has effects is one that can have them. What is not is not told that it has none,
            // it has something else at this place
            if (!particles.isEmpty() || entity.isEffectParticles() || entityType == EntityType.PLAYER) {
                entity.setEffectParticles(!particles.isEmpty());
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_EFFECT_PARTICLES, MetadataTypes.PARTICLES, particles));
            }
        } else if (entityType == EntityType.PLAYER && entityData.containsKey(EntityDataTypes.EFFECT_COLOR)) {
            // Older servers mix the colors of the effects into one
            int color = entityData.get(EntityDataTypes.EFFECT_COLOR);
            javaEntityData.add(new ObjectEntityMetadata<>(JAVA_EFFECT_PARTICLES, MetadataTypes.PARTICLES, color == 0 ? new ArrayList<Particle>() : List.of(getEffectParticle(color))));
        }

        if (JAVA_AGEABLE_ENTITIES.contains(entityType) && flags) {
            javaEntityData.add(new BooleanEntityMetadata(JAVA_BABY, MetadataTypes.BOOLEAN, entityData.getFlag(EntityFlag.BABY)));
        }
        if (entityType == EntityType.SHEEP && (flags || entityData.containsKey(EntityDataTypes.COLOR))) {
            // Java has the color of the wool and whether it is shorn in one number
            if (entityData.containsKey(EntityDataTypes.COLOR)) {
                entity.setColor(entityData.get(EntityDataTypes.COLOR));
            }
            if (flags) {
                entity.setSheared(entityData.getFlag(EntityFlag.SHEARED));
            }
            javaEntityData.add(new ByteEntityMetadata(JAVA_SHEEP_WOOL, MetadataTypes.BYTE, (byte) ((entity.getColor() & 0x0F) | (entity.isSheared() ? JAVA_SHEEP_SHEARED : 0))));
        } else if (entityType == EntityType.CREEPER && flags) {
            javaEntityData.add(new IntEntityMetadata(JAVA_CREEPER_SWELLING, MetadataTypes.INT, entityData.getFlag(EntityFlag.IGNITED) ? 1 : -1));
            javaEntityData.add(new BooleanEntityMetadata(JAVA_CREEPER_CHARGED, MetadataTypes.BOOLEAN, entityData.getFlag(EntityFlag.POWERED)));
            javaEntityData.add(new BooleanEntityMetadata(JAVA_CREEPER_IGNITED, MetadataTypes.BOOLEAN, entityData.getFlag(EntityFlag.IGNITED)));
        }
        return javaEntityData.toArray(new EntityMetadata<?, ?>[0]);
    }

    private static Particle getEffectParticle(int color) {
        return new Particle(ParticleType.ENTITY_EFFECT, new ColorParticleData(0xFF000000 | color));
    }
}
