/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.barrelmc.barrel.utils.FileManager;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class BlockConverter {

    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE = new HashMap<>();
    // Servers can use a hash of the block state instead of its index in the block palette
    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE = new HashMap<>();
    public static final HashMap<Integer, Integer> WATERLOGGED_JAVA_BLOCK = new HashMap<>();
    public static final BitSet JAVA_WATER_BLOCK = new BitSet();
    public static final BitSet JAVA_FLUID_BLOCK = new BitSet();
    // The first java block state of a door, for every one of its states. A door has 64 of them, counted through
    // by which way it faces, which half it is, on which side the hinge is, whether it is open and whether it is
    // powered. The bits of a state, from the first state of its door on:
    private static final HashMap<Integer, Integer> JAVA_DOOR_FIRST_STATE = new HashMap<>();
    private static final int DOOR_STATES = 64;
    private static final int DOOR_FACING = 0b110000;
    private static final int DOOR_LOWER = 0b001000;
    private static final int DOOR_HINGE = 0b000100;
    private static final int DOOR_OPEN = 0b000010;

    @Getter
    private static int javaBlockStateCount = 0;
    // What a bedrock server calls air, by the place of the block in its list and by the hash of the block
    private static int bedrockAirRuntimeId;
    private static int bedrockAirHash;

    public static void init() {
        JsonObject jsonObject = FileManager.getJsonObjectFromResource("runtime_blocks.json");

        assert jsonObject != null;

        Map<String, TreeSet<Integer>> doors = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            Integer bedrockRuntimeId = Integer.valueOf(entry.getKey());
            JsonObject blockEntry = entry.getValue().getAsJsonObject();
            Integer javaStateId = blockEntry.get("java_default_state").getAsInt();
            String bedrockName = blockEntry.get("bedrock_name").getAsString();

            BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE.put(bedrockRuntimeId, javaStateId);
            BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE.put(blockEntry.get("bedrock_network_id").getAsInt(), javaStateId);
            javaBlockStateCount = Math.max(javaBlockStateCount, javaStateId + 1);
            if (bedrockName.equals("minecraft:air")) {
                bedrockAirRuntimeId = bedrockRuntimeId;
                bedrockAirHash = blockEntry.get("bedrock_network_id").getAsInt();
            }
            if (bedrockName.equals("minecraft:water") || bedrockName.equals("minecraft:flowing_water")) {
                JAVA_WATER_BLOCK.set(javaStateId);
            }
            if (bedrockName.endsWith("_door")) {
                doors.computeIfAbsent(bedrockName, name -> new TreeSet<>()).add(javaStateId);
            }
            if (blockEntry.has("java_fluid")) {
                JAVA_FLUID_BLOCK.set(javaStateId);
            }
            JsonElement waterlogged = blockEntry.get("java_waterlogged_state");
            if (waterlogged != null) {
                WATERLOGGED_JAVA_BLOCK.put(javaStateId, waterlogged.getAsInt());
                JAVA_FLUID_BLOCK.set(waterlogged.getAsInt());
                javaBlockStateCount = Math.max(javaBlockStateCount, waterlogged.getAsInt() + 1);
            }
        }
        doors.values().forEach(BlockConverter::addJavaDoor);
    }

    // A bedrock door has every state of a java door that is not powered, which is every second one from the second
    // on. A door that is not known like this is left as it is
    private static void addJavaDoor(TreeSet<Integer> javaStates) {
        int first = javaStates.first() - 1;
        int state = first + 1;
        for (int javaState : javaStates) {
            if (javaState != state) {
                return;
            }
            state += 2;
        }
        if (state != first + 1 + DOOR_STATES) {
            return;
        }
        for (int i = 0; i < DOOR_STATES; i++) {
            JAVA_DOOR_FIRST_STATE.put(first + i, first);
        }
    }

    public static boolean isJavaDoorHalf(int javaBlockId) {
        return JAVA_DOOR_FIRST_STATE.containsKey(javaBlockId);
    }

    public static boolean isJavaDoorLower(int javaBlockId) {
        Integer first = JAVA_DOOR_FIRST_STATE.get(javaBlockId);
        return first != null && (javaBlockId - first & DOOR_LOWER) != 0;
    }

    // Whether the two are the lower and the upper half of the same kind of door
    public static boolean isJavaDoor(int lowerHalf, int upperHalf) {
        Integer first = JAVA_DOOR_FIRST_STATE.get(lowerHalf);
        return first != null && first.equals(JAVA_DOOR_FIRST_STATE.get(upperHalf)) && (lowerHalf - first & DOOR_LOWER) != 0 && (upperHalf - first & DOOR_LOWER) == 0;
    }

    // A bedrock server keeps which way a door faces and whether it is open with the lower half, and on which side
    // the hinge is with the upper half. Its clients put the two together, and what the other half says of these is
    // not kept up. A java client shows each half as it is told, so the halves are put together for it
    public static int getJavaDoorLower(int lowerHalf, int upperHalf) {
        int first = JAVA_DOOR_FIRST_STATE.get(lowerHalf);
        return first + (lowerHalf - first & ~DOOR_HINGE | upperHalf - first & DOOR_HINGE);
    }

    public static int getJavaDoorUpper(int lowerHalf, int upperHalf) {
        int first = JAVA_DOOR_FIRST_STATE.get(lowerHalf);
        return first + (upperHalf - first & ~(DOOR_FACING | DOOR_OPEN) | lowerHalf - first & (DOOR_FACING | DOOR_OPEN));
    }

    public static int getBedrockAirId(boolean hashed) {
        return hashed ? bedrockAirHash : bedrockAirRuntimeId;
    }

    // Convert mc bedrock runtime block id to java block state id
    public static int bedrockRuntimeToJavaStateId(int bedrockBlockId, boolean hashed) {
        return (hashed ? BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE : BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE).getOrDefault(bedrockBlockId, 1);
    }

    public static int javaBlockToWaterlogged(int javaBlockId) {
        return WATERLOGGED_JAVA_BLOCK.getOrDefault(javaBlockId, 1);
    }

    public static boolean isJavaWater(int javaBlockId) {
        return JAVA_WATER_BLOCK.get(javaBlockId);
    }

    public static boolean isJavaFluid(int javaBlockId) {
        return JAVA_FLUID_BLOCK.get(javaBlockId);
    }
}
