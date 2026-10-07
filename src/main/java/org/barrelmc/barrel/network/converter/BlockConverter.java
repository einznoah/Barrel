/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.barrelmc.barrel.utils.FileManager;

import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class BlockConverter {

    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE = new HashMap<>();
    // Servers can use a hash of the block state instead of its index in the block palette
    public static final HashMap<Integer, Integer> BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE = new HashMap<>();
    // What a bedrock server calls a block, which tells how the block sounds
    private static final HashMap<Integer, String> BEDROCK_BLOCK_RUNTIME_TO_NAME = new HashMap<>();
    private static final HashMap<Integer, String> BEDROCK_BLOCK_HASH_TO_NAME = new HashMap<>();
    public static final HashMap<Integer, Integer> WATERLOGGED_JAVA_BLOCK = new HashMap<>();
    public static final BitSet JAVA_WATER_BLOCK = new BitSet();
    public static final BitSet JAVA_FLUID_BLOCK = new BitSet();
    // The java blocks a client draws through what they hold, a chest or a sign for example, with the kind of that.
    // A java client that is not told of it for a block that comes with a chunk does not draw the block at all
    private static final HashMap<Integer, BlockEntityType> JAVA_BLOCK_ENTITY = new HashMap<>();
    // For a bedrock server a bed, a banner, a head on the floor, a flower pot and a lectern are the same block
    // whatever color they have, which way the head looks, what grows in the pot and whether a book lies on the
    // lectern: that is told with what the block holds. For a java client each of these is a block or a state of
    // its own. These are the java blocks the bedrock ones are without knowing what they hold: a white bed, a
    // white banner, a head that looks north, an empty pot and a lectern without a book
    private static final Set<Integer> JAVA_BEDS = new HashSet<>();
    private static final Set<Integer> JAVA_STANDING_BANNERS = new HashSet<>();
    private static final Set<Integer> JAVA_WALL_BANNERS = new HashSet<>();
    private static final Set<Integer> JAVA_FLOOR_HEADS = new HashSet<>();
    private static final Set<Integer> JAVA_LECTERNS = new HashSet<>();
    private static final Set<Integer> JAVA_BARRELS = new HashSet<>();
    // The java blocks of a chest that stands by itself, with the way it faces as a java client counts them: north,
    // south, west, east. The left and the right half of a large chest are java blocks of their own, this far on
    private static final HashMap<Integer, Integer> JAVA_CHEST_FACING = new HashMap<>();
    private static final int CHEST_LEFT = 2;
    private static final int CHEST_RIGHT = 4;
    // Where the right of a chest is, by the way it faces: one on x and one on z
    private static final int[][] CHEST_RIGHT_SIDE = {{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
    private static int javaFlowerPot = -1;
    // The java block of a pot with a plant in it, by the bedrock block of the plant
    private static final HashMap<String, Integer> JAVA_POTTED_PLANTS = new HashMap<>();
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
        Map<String, TreeSet<Integer>> heads = new HashMap<>();
        Map<String, TreeSet<Integer>> chests = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            Integer bedrockRuntimeId = Integer.valueOf(entry.getKey());
            JsonObject blockEntry = entry.getValue().getAsJsonObject();
            Integer javaStateId = blockEntry.get("java_default_state").getAsInt();
            String bedrockName = blockEntry.get("bedrock_name").getAsString();

            BEDROCK_BLOCK_RUNTIME_TO_JAVA_BLOCK_STATE.put(bedrockRuntimeId, javaStateId);
            BEDROCK_BLOCK_HASH_TO_JAVA_BLOCK_STATE.put(blockEntry.get("bedrock_network_id").getAsInt(), javaStateId);
            BEDROCK_BLOCK_RUNTIME_TO_NAME.put(bedrockRuntimeId, bedrockName.intern());
            BEDROCK_BLOCK_HASH_TO_NAME.put(blockEntry.get("bedrock_network_id").getAsInt(), bedrockName.intern());
            javaBlockStateCount = Math.max(javaBlockStateCount, javaStateId + 1);
            if (bedrockName.equals("minecraft:air")) {
                bedrockAirRuntimeId = bedrockRuntimeId;
                bedrockAirHash = blockEntry.get("bedrock_network_id").getAsInt();
            }
            if (bedrockName.equals("minecraft:water") || bedrockName.equals("minecraft:flowing_water")) {
                JAVA_WATER_BLOCK.set(javaStateId);
            }
            BlockEntityType blockEntity = getJavaBlockEntity(bedrockName);
            if (blockEntity != null) {
                JAVA_BLOCK_ENTITY.put(javaStateId, blockEntity);
                if (blockEntry.has("java_waterlogged_state")) {
                    JAVA_BLOCK_ENTITY.put(blockEntry.get("java_waterlogged_state").getAsInt(), blockEntity);
                }
            }
            switch (bedrockName) {
                case "minecraft:bed" -> JAVA_BEDS.add(javaStateId);
                case "minecraft:standing_banner" -> JAVA_STANDING_BANNERS.add(javaStateId);
                case "minecraft:wall_banner" -> JAVA_WALL_BANNERS.add(javaStateId);
                case "minecraft:lectern" -> JAVA_LECTERNS.add(javaStateId);
                case "minecraft:barrel" -> JAVA_BARRELS.add(javaStateId);
                case "minecraft:flower_pot" -> javaFlowerPot = javaStateId;
                default -> {
                }
            }
            if (blockEntity == BlockEntityType.CHEST || blockEntity == BlockEntityType.TRAPPED_CHEST) {
                chests.computeIfAbsent(bedrockName, name -> new TreeSet<>()).add(javaStateId);
            }
            if (blockEntity == BlockEntityType.SKULL) {
                heads.computeIfAbsent(bedrockName, name -> new TreeSet<>()).add(javaStateId);
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
        // A chest has a java block for each of the four ways it faces, in the order a java client counts them
        for (TreeSet<Integer> javaStates : chests.values()) {
            int facing = 0;
            for (int javaState : javaStates) {
                if (javaStates.size() == CHEST_RIGHT_SIDE.length) {
                    JAVA_CHEST_FACING.put(javaState, facing);
                    Integer waterlogged = WATERLOGGED_JAVA_BLOCK.get(javaState);
                    if (waterlogged != null) {
                        JAVA_CHEST_FACING.put(waterlogged, facing);
                    }
                }
                facing++;
            }
        }
        // Of the java blocks of a head the one on the floor comes first, the others hang on a wall
        heads.values().forEach(javaStates -> JAVA_FLOOR_HEADS.add(javaStates.first()));

        JsonObject pottedPlants = FileManager.getJsonObjectFromResource("potted_plants.json");
        if (pottedPlants != null) {
            for (Map.Entry<String, JsonElement> entry : pottedPlants.entrySet()) {
                JAVA_POTTED_PLANTS.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
    }

    public static boolean isJavaBed(int javaBlockId) {
        return JAVA_BEDS.contains(javaBlockId);
    }

    public static boolean isJavaStandingBanner(int javaBlockId) {
        return JAVA_STANDING_BANNERS.contains(javaBlockId);
    }

    public static boolean isJavaWallBanner(int javaBlockId) {
        return JAVA_WALL_BANNERS.contains(javaBlockId);
    }

    public static boolean isJavaFloorHead(int javaBlockId) {
        return JAVA_FLOOR_HEADS.contains(javaBlockId);
    }

    public static boolean isJavaLectern(int javaBlockId) {
        return JAVA_LECTERNS.contains(javaBlockId);
    }

    public static boolean isJavaChest(int javaBlockId) {
        return JAVA_CHEST_FACING.containsKey(javaBlockId);
    }

    // The java block of a chest that is one half of a large chest, by where its other half is from it. For a java
    // client the left half is the one that has the other half to the right of where it faces
    public static int getJavaChest(int javaBlockId, int otherX, int otherZ) {
        Integer facing = JAVA_CHEST_FACING.get(javaBlockId);
        if (facing == null) {
            return javaBlockId;
        }
        int[] right = CHEST_RIGHT_SIDE[facing];
        if (otherX == right[0] && otherZ == right[1]) {
            return javaBlockId + CHEST_LEFT;
        } else if (otherX == -right[0] && otherZ == -right[1]) {
            return javaBlockId + CHEST_RIGHT;
        }
        return javaBlockId;
    }

    public static boolean isJavaBarrel(int javaBlockId) {
        return JAVA_BARRELS.contains(javaBlockId);
    }

    public static boolean isJavaFlowerPot(int javaBlockId) {
        return javaBlockId == javaFlowerPot;
    }

    // The java block of a pot this bedrock block grows in, null for what a pot does not take
    public static Integer getJavaPottedPlant(String bedrockPlant) {
        return JAVA_POTTED_PLANTS.get(bedrockPlant);
    }

    // The kinds are told by the names of the bedrock blocks, which only differ from the java ones in the wood
    private static BlockEntityType getJavaBlockEntity(String bedrockName) {
        String name = bedrockName.substring(bedrockName.indexOf(':') + 1);
        if (name.endsWith("_hanging_sign")) {
            return BlockEntityType.HANGING_SIGN;
        } else if (name.endsWith("standing_sign") || name.endsWith("wall_sign")) {
            return BlockEntityType.SIGN;
        } else if (name.equals("chest") || name.endsWith("copper_chest")) {
            return BlockEntityType.CHEST;
        } else if (name.endsWith("shulker_box")) {
            return BlockEntityType.SHULKER_BOX;
        } else if (name.endsWith("_head") || name.endsWith("_skull")) {
            return BlockEntityType.SKULL;
        } else if (name.endsWith("_shelf")) {
            return BlockEntityType.SHELF;
        } else if (name.endsWith("copper_golem_statue")) {
            return BlockEntityType.COPPER_GOLEM_STATUE;
        }
        return switch (name) {
            case "trapped_chest" -> BlockEntityType.TRAPPED_CHEST;
            case "ender_chest" -> BlockEntityType.ENDER_CHEST;
            case "standing_banner", "wall_banner" -> BlockEntityType.BANNER;
            case "bell" -> BlockEntityType.BELL;
            case "enchanting_table" -> BlockEntityType.ENCHANTING_TABLE;
            case "end_portal" -> BlockEntityType.END_PORTAL;
            case "end_gateway" -> BlockEntityType.END_GATEWAY;
            case "conduit" -> BlockEntityType.CONDUIT;
            case "decorated_pot" -> BlockEntityType.DECORATED_POT;
            case "mob_spawner" -> BlockEntityType.MOB_SPAWNER;
            case "trial_spawner" -> BlockEntityType.TRIAL_SPAWNER;
            case "vault" -> BlockEntityType.VAULT;
            case "lectern" -> BlockEntityType.LECTERN;
            case "beacon" -> BlockEntityType.BEACON;
            case "campfire", "soul_campfire" -> BlockEntityType.CAMPFIRE;
            default -> null;
        };
    }

    // What kind of thing a java block holds that it is drawn through, null for a block that is drawn as it is
    public static BlockEntityType getJavaBlockEntity(int javaBlockId) {
        return JAVA_BLOCK_ENTITY.get(javaBlockId);
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

    // What a bedrock server calls the block, null for a number that is not one of a block
    public static String getBedrockName(int bedrockBlockId, boolean hashed) {
        return (hashed ? BEDROCK_BLOCK_HASH_TO_NAME : BEDROCK_BLOCK_RUNTIME_TO_NAME).get(bedrockBlockId);
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
