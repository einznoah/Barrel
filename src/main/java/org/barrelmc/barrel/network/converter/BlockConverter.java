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

public class BlockConverter {

    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE = new HashMap<>();
    // Servers can use a hash of the block state instead of its index in the block palette
    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE = new HashMap<>();
    public static final HashMap<Integer, Integer> WATERLOGGED_JAVA_BLOCK = new HashMap<>();
    public static final BitSet JAVA_WATER_BLOCK = new BitSet();
    public static final BitSet JAVA_FLUID_BLOCK = new BitSet();

    @Getter
    private static int javaBlockStateCount = 0;
    // What a bedrock server calls air, by the place of the block in its list and by the hash of the block
    private static int bedrockAirRuntimeId;
    private static int bedrockAirHash;

    public static void init() {
        JsonObject jsonObject = FileManager.getJsonObjectFromResource("runtime_blocks.json");

        assert jsonObject != null;

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
