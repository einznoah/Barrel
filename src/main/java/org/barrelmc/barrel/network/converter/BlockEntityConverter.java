package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;

import java.util.ArrayList;
import java.util.List;

// What a block holds, the text of a sign for one. A bedrock server sends it with the chunk and when it changes
public class BlockEntityConverter {

    private static final int SIGN_LINES = 4;
    // The colors a sign can be written in, a java client has them by name and a bedrock server as the color itself
    private static final String[] DYE_NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] DYE_COLORS = {0xFFFFFF, 0xFF681F, 0xFF00FF, 0x9AC0CD, 0xFFFF00, 0xBFFF00, 0xFF69B4, 0x808080, 0xD3D3D3, 0x00FFFF, 0xA020F0, 0x0000FF, 0x8B4513, 0x00FF00, 0xFF0000, 0x000000};

    public static boolean isTranslated(BlockEntityType type) {
        return type == BlockEntityType.SIGN || type == BlockEntityType.HANGING_SIGN;
    }

    // What the java client is told a block of this kind holds. For most kinds that is nothing, the client only has
    // to know that the block is there to draw it
    public static NbtMap bedrockToJava(BlockEntityType type, NbtMap bedrock) {
        if (bedrock == null || !isTranslated(type)) {
            return NbtMap.EMPTY;
        }

        NbtMapBuilder sign = NbtMap.builder();
        // Before signs had a back, the text was not in a part of its own
        sign.putCompound("front_text", getSignText(bedrock.containsKey("FrontText", NbtType.COMPOUND) ? bedrock.getCompound("FrontText") : bedrock));
        sign.putCompound("back_text", getSignText(bedrock.getCompound("BackText")));
        sign.putBoolean("is_waxed", bedrock.getBoolean("IsWaxed", false));
        return sign.build();
    }

    private static NbtMap getSignText(NbtMap bedrockText) {
        // A bedrock sign has its lines as one text, a java sign has four of them
        String[] bedrockLines = bedrockText.getString("Text", "").split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (int line = 0; line < SIGN_LINES; line++) {
            lines.add(line < bedrockLines.length ? bedrockLines[line].replaceAll("\u00a7.", "") : "");
        }

        NbtMapBuilder text = NbtMap.builder();
        text.putList("messages", NbtType.STRING, lines);
        text.putString("color", getDyeName(bedrockText.getInt("SignTextColor", 0xFF000000)));
        text.putBoolean("has_glowing_text", bedrockText.getBoolean("IgnoreLighting", false) && !bedrockText.getBoolean("HideGlowOutline", false));
        return text.build();
    }

    // A sign as a bedrock client sends it after the player has written one side of it. What was not written stays
    // as the server sent it
    public static NbtMap getWrittenSign(BlockEntityType type, Vector3i position, NbtMap bedrock, List<String> lines, boolean front) {
        NbtMap old = bedrock == null ? NbtMap.EMPTY : bedrock;
        // The lines at the end that nothing is written on are not sent
        int written = lines.size();
        while (written > 0 && lines.get(written - 1).isEmpty()) {
            written--;
        }
        String text = String.join("\n", lines.subList(0, written));

        NbtMapBuilder sign = NbtMap.builder();
        sign.putString("id", type == BlockEntityType.HANGING_SIGN ? "HangingSign" : "Sign");
        sign.putInt("x", position.getX());
        sign.putInt("y", position.getY());
        sign.putInt("z", position.getZ());
        sign.putCompound("FrontText", getWrittenText(old.getCompound("FrontText"), front ? text : null));
        sign.putCompound("BackText", getWrittenText(old.getCompound("BackText"), front ? null : text));
        sign.putBoolean("IsWaxed", old.getBoolean("IsWaxed", false));
        sign.putLong("LockedForEditingBy", -1L);
        return sign.build();
    }

    private static NbtMap getWrittenText(NbtMap old, String text) {
        return NbtMap.builder()
                .putString("FilteredText", old.getString("FilteredText", ""))
                .putBoolean("HideGlowOutline", old.getBoolean("HideGlowOutline", false))
                .putBoolean("IgnoreLighting", old.getBoolean("IgnoreLighting", false))
                .putBoolean("PersistFormatting", old.getBoolean("PersistFormatting", true))
                .putInt("SignTextColor", old.getInt("SignTextColor", 0xFF000000))
                .putString("Text", text == null ? old.getString("Text", "") : text)
                .putString("TextOwner", old.getString("TextOwner", ""))
                .build();
    }

    // The dye that is closest to the color
    private static String getDyeName(int color) {
        int closest = DYE_COLORS.length - 1;
        int closestDistance = Integer.MAX_VALUE;
        for (int dye = 0; dye < DYE_COLORS.length; dye++) {
            int red = (color >> 16 & 0xFF) - (DYE_COLORS[dye] >> 16 & 0xFF);
            int green = (color >> 8 & 0xFF) - (DYE_COLORS[dye] >> 8 & 0xFF);
            int blue = (color & 0xFF) - (DYE_COLORS[dye] & 0xFF);
            int distance = red * red + green * green + blue * blue;
            if (distance < closestDistance) {
                closest = dye;
                closestDistance = distance;
            }
        }
        return DYE_NAMES[closest];
    }
}
