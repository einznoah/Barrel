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

    // The colors of the swirls of the effects, in the order of their bedrock ids
    private static final int[] BEDROCK_EFFECT_COLORS = {
            0x33EBFF, 0x8BAFE0, 0xD9C043, 0x4A4217, 0xFFC700, 0xF82423, 0xA9656A, 0xFDFF84, 0x551D4A, 0xCD5CAB, 0x9146F0, 0xFF9900, 0x98DAC0,
            0xF6F6F6, 0x1F1F23, 0xC2FF66, 0x587653, 0x484D48, 0x87A363, 0x736156, 0xF87D23, 0x2552A5, 0xF82423, 0xCEFFFF, 0x4E9331, 0x1DC2D1,
            0xF3CFB9, 0x0B6138, 0x44FF44, 0x292721, 0x16A6A6, 0xBDC9FF, 0x78695A, 0x99FFA3, 0x8C9B8C, 0xDE4058, 0x00FFEE
    };

    // The potion of decay only bedrock has, it withers
    public static final int BEDROCK_DECAY_POTION = 36;
    public static final int DECAY_COLOR = BEDROCK_EFFECT_COLORS[19];
    public static final int DECAY_TICKS = 800;

    // Returns -1 for an effect that is not known
    public static int getEffectColor(int bedrockEffectId) {
        return bedrockEffectId >= 1 && bedrockEffectId <= BEDROCK_EFFECT_COLORS.length ? BEDROCK_EFFECT_COLORS[bedrockEffectId - 1] : -1;
    }

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
