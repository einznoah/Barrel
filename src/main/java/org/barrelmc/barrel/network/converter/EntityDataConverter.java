package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ColorParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// What an entity is like is numbered by the kind of entity on java. A java client disconnects if it is sent something
// under a number its kind of entity has something else at, so only what is known to be right is translated
public class EntityDataConverter {

    private static final int JAVA_EFFECT_PARTICLES = 10;
    private static final int JAVA_BABY = 16;
    private static final int JAVA_CLOUD_RADIUS = 8;
    private static final int JAVA_CLOUD_PARTICLE = 10;

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

    public static EntityMetadata<?, ?>[] bedrockToJavaEntityData(EntityType entityType, EntityDataMap entityData) {
        List<EntityMetadata<?, ?>> javaEntityData = new ArrayList<>();
        if (entityType == EntityType.AREA_EFFECT_CLOUD) {
            // What a lingering potion leaves behind
            if (entityData.containsKey(EntityDataTypes.AREA_EFFECT_CLOUD_RADIUS)) {
                javaEntityData.add(new FloatEntityMetadata(JAVA_CLOUD_RADIUS, MetadataTypes.FLOAT, entityData.get(EntityDataTypes.AREA_EFFECT_CLOUD_RADIUS)));
            }
            if (entityData.containsKey(EntityDataTypes.EFFECT_COLOR)) {
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_CLOUD_PARTICLE, MetadataTypes.PARTICLE, getEffectParticle(entityData.get(EntityDataTypes.EFFECT_COLOR))));
            }
        } else if (entityType == EntityType.PLAYER) {
            // The swirls of the effects a player has
            if (entityData.containsKey(EntityDataTypes.VISIBLE_MOB_EFFECTS)) {
                List<Particle> particles = new ArrayList<>();
                for (long visibleEffects = entityData.get(EntityDataTypes.VISIBLE_MOB_EFFECTS); visibleEffects != 0; visibleEffects >>>= VISIBLE_EFFECT_BITS) {
                    // The last bit tells whether the effect is one of a beacon
                    int color = PotionConverter.getEffectColor((int) (visibleEffects & ((1 << VISIBLE_EFFECT_BITS) - 1)) >> 1);
                    if (color != -1) {
                        particles.add(getEffectParticle(color));
                    }
                }
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_EFFECT_PARTICLES, MetadataTypes.PARTICLES, particles));
            } else if (entityData.containsKey(EntityDataTypes.EFFECT_COLOR)) {
                // Older servers mix the colors of the effects into one
                int color = entityData.get(EntityDataTypes.EFFECT_COLOR);
                javaEntityData.add(new ObjectEntityMetadata<>(JAVA_EFFECT_PARTICLES, MetadataTypes.PARTICLES, color == 0 ? new ArrayList<Particle>() : List.of(getEffectParticle(color))));
            }
        } else if (JAVA_AGEABLE_ENTITIES.contains(entityType) && entityData.getFlags() != null) {
            javaEntityData.add(new BooleanEntityMetadata(JAVA_BABY, MetadataTypes.BOOLEAN, entityData.getFlag(EntityFlag.BABY)));
        }
        return javaEntityData.toArray(new EntityMetadata<?, ?>[0]);
    }

    private static Particle getEffectParticle(int color) {
        return new Particle(ParticleType.ENTITY_EFFECT, new ColorParticleData(0xFF000000 | color));
    }
}
