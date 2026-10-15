package org.barrelmc.barrel.network.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.barrelmc.barrel.utils.FileManager;

import java.util.Base64;

// How much light each java block gives, how much it takes away from light that goes through it, and which parts of
// its sides stop light, as the java game has it. Blocks that are alike in this are one kind of light. The data is
// in block_light.json, which tools/GenerateBlockLight.java makes
public class LightConverter {

    // The sides of a block, in the order of block_light.json
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int SIDES = 6;
    // The side across from each side
    private static final int[] OPPOSITE = {1, 0, 3, 2, 5, 4};
    // A side is 16 by 16 squares, kept as four numbers of 64 bits
    private static final int FACE_WORDS = 4;

    private static byte[] javaKinds = new byte[0];
    private static int[] emission = {0};
    // Light goes one less from block to block, or by what the block takes away when that is more
    private static int[] opacity = {1};
    private static int[] dampening = {0};
    // The parts of each side of a kind that stop light, by a number into the list of sides, 0 for none
    private static int[][] faces = {new int[SIDES]};
    // Whether two sides that touch stop light together
    private static boolean[][] facesOcclude = {{false}};
    // Whether a kind stops light by none of its sides
    private static boolean[] noFaces = {true};

    public static void init() {
        JsonObject json = FileManager.getJsonObjectFromResource("block_light.json");
        if (json == null) {
            return;
        }

        JsonArray faceList = json.getAsJsonArray("faces");
        long[][] faceBits = new long[faceList.size()][FACE_WORDS];
        for (int face = 0; face < faceList.size(); face++) {
            String hex = faceList.get(face).getAsString();
            int perWord = hex.length() / FACE_WORDS;
            for (int word = 0; word < FACE_WORDS; word++) {
                faceBits[face][word] = Long.parseUnsignedLong(hex.substring(word * perWord, (word + 1) * perWord), 16);
            }
        }
        // Two sides stop light when what both of them cover together is all of a side
        facesOcclude = new boolean[faceBits.length][faceBits.length];
        for (int a = 0; a < faceBits.length; a++) {
            for (int b = 0; b < faceBits.length; b++) {
                boolean all = true;
                for (int word = 0; word < FACE_WORDS; word++) {
                    all &= (faceBits[a][word] | faceBits[b][word]) == -1L;
                }
                facesOcclude[a][b] = all;
            }
        }

        JsonArray kinds = json.getAsJsonArray("kinds");
        emission = new int[kinds.size()];
        opacity = new int[kinds.size()];
        dampening = new int[kinds.size()];
        faces = new int[kinds.size()][SIDES];
        noFaces = new boolean[kinds.size()];
        for (int kind = 0; kind < kinds.size(); kind++) {
            JsonArray light = kinds.get(kind).getAsJsonArray();
            emission[kind] = light.get(0).getAsInt();
            dampening[kind] = light.get(1).getAsInt();
            opacity[kind] = Math.max(1, dampening[kind]);
            noFaces[kind] = true;
            for (int side = 0; side < SIDES; side++) {
                faces[kind][side] = light.get(2 + side).getAsInt();
                noFaces[kind] &= faces[kind][side] == 0;
            }
        }
        javaKinds = Base64.getDecoder().decode(json.get("states").getAsString());
    }

    // The kind of light of a java block, that of air for a number that is not one of a block
    public static int getKind(int javaStateId) {
        return javaStateId >= 0 && javaStateId < javaKinds.length ? javaKinds[javaStateId] & 0xFF : 0;
    }

    public static int getEmission(int kind) {
        return emission[kind];
    }

    public static int getOpacity(int kind) {
        return opacity[kind];
    }

    // Whether light that goes out of a block of one kind by a side and into a block of another kind is stopped by
    // the sides that touch
    public static boolean occludes(int fromKind, int toKind, int side) {
        if (noFaces[fromKind] && noFaces[toKind]) {
            return false;
        }
        return facesOcclude[faces[fromKind][side]][faces[toKind][OPPOSITE[side]]];
    }

    // Whether the light of the sky that comes down out of a block stops at the block below it: when that one takes
    // light away, or the sides between them stop it
    public static boolean stopsSkyLight(int aboveKind, int belowKind) {
        return dampening[belowKind] != 0 || occludes(aboveKind, belowKind, DOWN);
    }
}
