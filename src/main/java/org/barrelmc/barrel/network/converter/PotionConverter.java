package org.barrelmc.barrel.network.converter;

import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

public class PotionConverter {

    // The bedrock ids of the potions in the order of their java ids, -1 for the one bedrock does not have
    private static final int[] BEDROCK_POTION_IDS = {
            0, 1, 3, 4, // water, mundane, thick, awkward
            5, 6, // night vision
            7, 8, // invisibility
            9, 10, 11, // leaping
            12, 13, // fire resistance
            14, 15, 16, // swiftness
            17, 18, 42, // slowness
            37, 38, 39, // turtle master
            19, 20, // water breathing
            21, 22, // healing
            23, 24, // harming
            25, 26, 27, // poison
            28, 29, 30, // regeneration
            31, 32, 33, // strength
            34, 35, // weakness
            -1, // luck
            40, 41, // slow falling
            43, 44, 45, 46 // wind charged, weaving, oozing, infested
    };
    private static final int JAVA_MUNDANE_POTION = 1;
    private static final int BEDROCK_LONG_MUNDANE_POTION = 2;

    // The java effects in the order of their bedrock ids, which start at 1
    private static final Effect[] BEDROCK_EFFECTS = {
            Effect.SPEED, Effect.SLOWNESS, Effect.HASTE, Effect.MINING_FATIGUE, Effect.STRENGTH, Effect.INSTANT_HEALTH,
            Effect.INSTANT_DAMAGE, Effect.JUMP_BOOST, Effect.NAUSEA, Effect.REGENERATION, Effect.RESISTANCE,
            Effect.FIRE_RESISTANCE, Effect.WATER_BREATHING, Effect.INVISIBILITY, Effect.BLINDNESS, Effect.NIGHT_VISION,
            Effect.HUNGER, Effect.WEAKNESS, Effect.POISON, Effect.WITHER, Effect.HEALTH_BOOST, Effect.ABSORPTION,
            Effect.SATURATION, Effect.LEVITATION,
            // Fatal poison, java only has the poison that does not kill
            Effect.POISON,
            Effect.CONDUIT_POWER, Effect.SLOW_FALLING, Effect.BAD_OMEN, Effect.HERO_OF_THE_VILLAGE, Effect.DARKNESS,
            Effect.TRIAL_OMEN, Effect.WIND_CHARGED, Effect.WEAVING, Effect.OOZING, Effect.INFESTED, Effect.RAID_OMEN,
            Effect.BREATH_OF_THE_NAUTILUS
    };

    // Returns -1 for a potion java does not have
    public static int bedrockToJavaPotionId(int bedrockPotionId) {
        if (bedrockPotionId == BEDROCK_LONG_MUNDANE_POTION) {
            return JAVA_MUNDANE_POTION;
        }

        for (int javaPotionId = 0; javaPotionId < BEDROCK_POTION_IDS.length; javaPotionId++) {
            if (BEDROCK_POTION_IDS[javaPotionId] == bedrockPotionId) {
                return javaPotionId;
            }
        }
        return -1;
    }

    // Returns -1 for a potion bedrock does not have
    public static int javaToBedrockPotionId(int javaPotionId) {
        return javaPotionId >= 0 && javaPotionId < BEDROCK_POTION_IDS.length ? BEDROCK_POTION_IDS[javaPotionId] : -1;
    }

    // Returns null for an effect java does not have
    public static Effect bedrockToJavaEffect(int bedrockEffectId) {
        return bedrockEffectId >= 1 && bedrockEffectId <= BEDROCK_EFFECTS.length ? BEDROCK_EFFECTS[bedrockEffectId - 1] : null;
    }
}
