package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.barrelmc.barrel.utils.FileManager;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// A bedrock server tells of a sound by what happened, an event, with a number or the kind of an entity where that
// takes more to tell, or by the name the sound has for a bedrock client. A java client is told the sound itself.
// Which java sound that is comes from sounds.json, which tools/generate_sounds.py makes
public class SoundConverter {

    private static final Map<String, String> EVENTS = new HashMap<>();
    private static final Map<String, String> NAMED = new HashMap<>();
    // The kind of sound of the blocks that do not sound like stone: the part of the java sounds between "block."
    // and what is done to the block
    private static final Map<String, String> BLOCK_KINDS = new HashMap<>();
    private static final String STONE = "stone";
    private static final Map<String, BuiltinSound> JAVA_SOUNDS = new HashMap<>();

    // What is done to a block, for the events that are told with the block
    private static final Map<SoundEvent, String> BLOCK_EVENTS = new EnumMap<>(SoundEvent.class);
    // What opens and closes, which sounds by what it is made of
    private static final Map<SoundEvent, String> OPENING_EVENTS = new EnumMap<>(SoundEvent.class);
    // What is pressed, which a java client has a sound of wood and one of stone for
    private static final Map<SoundEvent, String> PRESSED_EVENTS = new EnumMap<>(SoundEvent.class);

    // The sound a mob makes now and then, the ticks it waits at least before it may make it again and how loud it
    // is, as the java game has them. A server of mojang does not tell these sounds, a bedrock client makes them
    public record MobSound(BuiltinSound sound, int interval, float volume) {
    }

    private static final Map<EntityType, MobSound> MOB_SOUNDS = new EnumMap<>(EntityType.class);
    // The mobs whose sound is not named after them
    private static final Map<EntityType, String> MOB_SOUND_NAMES = Map.of(
            EntityType.MOOSHROOM, "entity.cow.ambient",
            EntityType.TRADER_LLAMA, "entity.llama.ambient",
            EntityType.PUFFERFISH, "entity.puffer_fish.ambient",
            EntityType.ALLAY, "entity.allay.ambient_without_item",
            EntityType.TURTLE, "entity.turtle.ambient_land");
    private static final int MOB_INTERVAL = 80;
    // Animals, golems and what swims wait longer, horses and their kin much longer
    private static final Set<EntityType> ANIMALS = EnumSet.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.SHEEP,
            EntityType.CHICKEN, EntityType.RABBIT, EntityType.WOLF, EntityType.CAT, EntityType.FOX, EntityType.PANDA, EntityType.POLAR_BEAR,
            EntityType.BEE, EntityType.GOAT, EntityType.FROG, EntityType.AXOLOTL, EntityType.PARROT, EntityType.HOGLIN, EntityType.STRIDER,
            EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.HAPPY_GHAST, EntityType.DOLPHIN, EntityType.SQUID, EntityType.GLOW_SQUID,
            EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH, EntityType.TADPOLE, EntityType.SNOW_GOLEM,
            EntityType.IRON_GOLEM);
    private static final Set<EntityType> HORSES = EnumSet.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.SKELETON_HORSE,
            EntityType.ZOMBIE_HORSE, EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.CAMEL);
    private static final Map<EntityType, Integer> OWN_INTERVALS = Map.of(EntityType.OCELOT, 900, EntityType.TURTLE, 200,
            EntityType.GUARDIAN, 160, EntityType.ELDER_GUARDIAN, 160);
    private static final Map<EntityType, Float> OWN_VOLUMES = Map.ofEntries(Map.entry(EntityType.COW, 0.4F), Map.entry(EntityType.MOOSHROOM, 0.4F),
            Map.entry(EntityType.WOLF, 0.4F), Map.entry(EntityType.SQUID, 0.4F), Map.entry(EntityType.GLOW_SQUID, 0.4F), Map.entry(EntityType.ALLAY, 0.4F),
            Map.entry(EntityType.BEE, 0.4F), Map.entry(EntityType.SLIME, 0.4F), Map.entry(EntityType.MAGMA_CUBE, 0.4F), Map.entry(EntityType.BAT, 0.1F),
            Map.entry(EntityType.GHAST, 5F), Map.entry(EntityType.ENDER_DRAGON, 5F), Map.entry(EntityType.WARDEN, 4F));
    private static final float HORSE_VOLUME = 0.8F;
    // How a bedrock server tells what a mob says now and then
    private static final Set<SoundEvent> MOB_AMBIENT_EVENTS = EnumSet.of(SoundEvent.AMBIENT, SoundEvent.AMBIENT_BABY, SoundEvent.AMBIENT_IN_WATER,
            SoundEvent.AMBIENT_IN_AIR, SoundEvent.AMBIENT_TAME, SoundEvent.AMBIENT_AGGRESSIVE, SoundEvent.AMBIENT_WORRIED, SoundEvent.AMBIENT_IN_RAID);

    // The instrument of a note is told above its pitch, a note block has 25 pitches from two octaves
    private static final int NOTE_PITCH = 0xFF;
    private static final int NOTE_MIDDLE = 12;
    private static final float BABY_PITCH = 1.5F;
    // What a bedrock server calls the records of a jukebox
    private static final String RECORD = "record.";

    static {
        BLOCK_EVENTS.put(SoundEvent.STEP, "step");
        BLOCK_EVENTS.put(SoundEvent.HEAVY_STEP, "step");
        BLOCK_EVENTS.put(SoundEvent.JUMP, "step");
        BLOCK_EVENTS.put(SoundEvent.HIT, "hit");
        BLOCK_EVENTS.put(SoundEvent.PLACE, "place");
        BLOCK_EVENTS.put(SoundEvent.ITEM_USE_ON, "place");
        BLOCK_EVENTS.put(SoundEvent.BREAK, "break");
        BLOCK_EVENTS.put(SoundEvent.BREAK_BLOCK, "break");
        BLOCK_EVENTS.put(SoundEvent.FALL, "fall");
        BLOCK_EVENTS.put(SoundEvent.LAND, "fall");

        OPENING_EVENTS.put(SoundEvent.DOOR_OPEN, "door.open");
        OPENING_EVENTS.put(SoundEvent.DOOR_CLOSE, "door.close");
        OPENING_EVENTS.put(SoundEvent.TRAPDOOR_OPEN, "trapdoor.open");
        OPENING_EVENTS.put(SoundEvent.TRAPDOOR_CLOSE, "trapdoor.close");
        OPENING_EVENTS.put(SoundEvent.FENCE_GATE_OPEN, "fence_gate.open");
        OPENING_EVENTS.put(SoundEvent.FENCE_GATE_CLOSE, "fence_gate.close");

        PRESSED_EVENTS.put(SoundEvent.BUTTON_CLICK_ON, "button.click_on");
        PRESSED_EVENTS.put(SoundEvent.BUTTON_CLICK_OFF, "button.click_off");
        PRESSED_EVENTS.put(SoundEvent.PRESSURE_PLATE_CLICK_ON, "pressure_plate.click_on");
        PRESSED_EVENTS.put(SoundEvent.PRESSURE_PLATE_CLICK_OFF, "pressure_plate.click_off");
    }

    public static void init() {
        JsonObject sounds = FileManager.getJsonObjectFromResource("sounds.json");
        assert sounds != null;
        read(sounds.getAsJsonObject("events"), EVENTS);
        read(sounds.getAsJsonObject("playsounds"), NAMED);
        read(sounds.getAsJsonObject("blocks"), BLOCK_KINDS);
        for (BuiltinSound sound : BuiltinSound.values()) {
            JAVA_SOUNDS.put(sound.getName(), sound);
        }
        for (EntityType type : EntityType.values()) {
            BuiltinSound sound = JAVA_SOUNDS.get(MOB_SOUND_NAMES.getOrDefault(type, "entity." + type.name().toLowerCase(Locale.ROOT) + ".ambient"));
            if (sound != null && type != EntityType.PLAYER) {
                int interval = OWN_INTERVALS.getOrDefault(type, HORSES.contains(type) ? 400 : ANIMALS.contains(type) ? 120 : MOB_INTERVAL);
                MOB_SOUNDS.put(type, new MobSound(sound, interval, OWN_VOLUMES.getOrDefault(type, HORSES.contains(type) ? HORSE_VOLUME : 1F)));
            }
        }
    }

    // The sound a mob of a kind makes now and then, null for one that makes none
    public static MobSound getMobSound(EntityType type) {
        return MOB_SOUNDS.get(type);
    }

    private static void read(JsonObject from, Map<String, String> to) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            to.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    // The java sound for an event a bedrock server tells, null for one a java client has no sound for. The number
    // told with it is a block for what is done to a block, the bedrock name of which is given
    public static BuiltinSound getJavaSound(SoundEvent event, int extraData, String identifier, String bedrockBlock) {
        String blockEvent = BLOCK_EVENTS.get(event);
        if (blockEvent != null && bedrockBlock != null) {
            return get("block." + getBlockKind(bedrockBlock) + "." + blockEvent, "block." + STONE + "." + blockEvent);
        }
        String openingEvent = OPENING_EVENTS.get(event);
        if (openingEvent != null) {
            // A door of plain wood and one of iron are named by themselves, the others by what they are made of
            String kind = bedrockBlock == null ? "wood" : getBlockKind(bedrockBlock);
            String made = kind.equals("wood") || kind.equals(STONE) ? (openingEvent.startsWith("fence_gate") ? "" : "wooden_") : kind.equals("metal") ? "iron_" : kind + "_";
            return get("block." + made + openingEvent, "block." + (openingEvent.startsWith("fence_gate") ? "" : "wooden_") + openingEvent);
        }

        String pressedEvent = PRESSED_EVENTS.get(event);
        if (pressedEvent != null) {
            String kind = bedrockBlock == null ? STONE : getBlockKind(bedrockBlock);
            boolean wooden = kind.endsWith("wood") || kind.equals("stem");
            return get("block." + (wooden ? "wooden_" : kind.equals("metal") && pressedEvent.startsWith("pressure_plate") ? "metal_" : "stone_") + pressedEvent, "block.stone_" + pressedEvent);
        }

        String name = event.name();
        if (event == SoundEvent.NOTE) {
            return get(EVENTS.get(name + "|" + (extraData & ~NOTE_PITCH)), null);
        }
        if (identifier != null && !identifier.isEmpty()) {
            // The sounds of entities are kept by what a java client calls the entity
            EntityType javaEntity = EntityConverter.bedrockToJavaEntityType(identifier);
            String entity = javaEntity != null ? javaEntity.name().toLowerCase(Locale.ROOT) : identifier.substring(identifier.indexOf(':') + 1);
            BuiltinSound sound = get(EVENTS.get(name + "|" + entity), EVENTS.get(name + "|" + identifier));
            if (sound != null) {
                return sound;
            }
            // What a mob says now and then is its own sound or none, and not what a place sounds like
            if (MOB_AMBIENT_EVENTS.contains(event)) {
                MobSound mobSound = javaEntity == null ? null : MOB_SOUNDS.get(javaEntity);
                return mobSound == null ? null : mobSound.sound();
            }
        }
        BuiltinSound sound = get(EVENTS.get(name + "|" + extraData), EVENTS.get(name));
        // What a player does sounds like a player only when it is a player: a wolf that bites does not swing a sword
        boolean ofPlayer = identifier == null || identifier.isEmpty() || identifier.equals("minecraft:player");
        return sound != null ? sound : get(EVENTS.get(name + "|generic"), ofPlayer ? EVENTS.get(name + "|player") : null);
    }

    // A java client plays a sound as it is, higher or lower when it is told so: the note of a note block, and the
    // voice of a young animal
    public static float getPitch(SoundEvent event, int extraData, boolean baby) {
        if (event == SoundEvent.NOTE) {
            return (float) Math.pow(2, ((extraData & NOTE_PITCH) - NOTE_MIDDLE) / 12.0);
        }
        return baby ? BABY_PITCH : 1;
    }

    // The java sound for the name a bedrock client has for a sound, null for none
    public static BuiltinSound getJavaSound(String bedrockName) {
        return get(NAMED.get(bedrockName), null);
    }

    // The song a java client has for a record a jukebox plays, null for a sound that is not a record
    public static String getJukeboxSong(String bedrockName) {
        return bedrockName.startsWith(RECORD) ? "minecraft:" + bedrockName.substring(RECORD.length()) : null;
    }

    // Where a java client lets the player turn the sound up and down
    public static SoundCategory getCategory(BuiltinSound sound) {
        String name = sound.getName();
        if (name.startsWith("music_disc.")) {
            return SoundCategory.RECORD;
        } else if (name.startsWith("block.")) {
            return SoundCategory.BLOCK;
        } else if (name.startsWith("entity.player.") || name.startsWith("item.")) {
            return SoundCategory.PLAYER;
        } else if (name.startsWith("entity.")) {
            return SoundCategory.NEUTRAL;
        } else if (name.startsWith("ambient.")) {
            return SoundCategory.AMBIENT;
        } else if (name.startsWith("weather.")) {
            return SoundCategory.WEATHER;
        }
        return SoundCategory.MASTER;
    }

    private static String getBlockKind(String bedrockBlock) {
        return BLOCK_KINDS.getOrDefault(bedrockBlock, STONE);
    }

    private static BuiltinSound get(String javaName, String otherwise) {
        BuiltinSound sound = javaName == null ? null : JAVA_SOUNDS.get(javaName);
        return sound != null || otherwise == null ? sound : JAVA_SOUNDS.get(otherwise);
    }
}
