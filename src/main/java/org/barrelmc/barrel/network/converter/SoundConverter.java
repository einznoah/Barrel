package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.barrelmc.barrel.utils.FileManager;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        }
        BuiltinSound sound = get(EVENTS.get(name + "|" + extraData), EVENTS.get(name));
        return sound != null ? sound : get(EVENTS.get(name + "|generic"), EVENTS.get(name + "|player"));
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
