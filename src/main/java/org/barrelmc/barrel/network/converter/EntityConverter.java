/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.converter;

import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.HashMap;
import java.util.Locale;

public class EntityConverter {

    // The bedrock entities that are not called what they are on java
    private static final HashMap<String, EntityType> BEDROCK_ENTITY_TO_JAVA_ENTITY = new HashMap<>();

    static {
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:villager_v2", EntityType.VILLAGER);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:zombie_villager_v2", EntityType.ZOMBIE_VILLAGER);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:zombie_pigman", EntityType.ZOMBIFIED_PIGLIN);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:evocation_illager", EntityType.EVOKER);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:tropicalfish", EntityType.TROPICAL_FISH);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:xp_orb", EntityType.EXPERIENCE_ORB);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:xp_bottle", EntityType.EXPERIENCE_BOTTLE);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:ender_crystal", EntityType.END_CRYSTAL);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:fireworks_rocket", EntityType.FIREWORK_ROCKET);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:thrown_trident", EntityType.TRIDENT);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:evocation_fang", EntityType.EVOKER_FANGS);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:eye_of_ender_signal", EntityType.EYE_OF_ENDER);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:wind_charge_projectile", EntityType.WIND_CHARGE);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:breeze_wind_charge_projectile", EntityType.BREEZE_WIND_CHARGE);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:boat", EntityType.OAK_BOAT);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:chest_boat", EntityType.OAK_CHEST_BOAT);
        // These need more than a type to be shown on java, or are spawned by packets of their own
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:item", null);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:player", null);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:falling_block", null);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:painting", null);
        BEDROCK_ENTITY_TO_JAVA_ENTITY.put("minecraft:fishing_hook", null);
    }

    // Returns null for the entities java does not have
    public static EntityType bedrockToJavaEntityType(String bedrockEntity) {
        if (BEDROCK_ENTITY_TO_JAVA_ENTITY.containsKey(bedrockEntity)) {
            return BEDROCK_ENTITY_TO_JAVA_ENTITY.get(bedrockEntity);
        }

        try {
            return EntityType.valueOf(bedrockEntity.substring(bedrockEntity.indexOf(':') + 1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
