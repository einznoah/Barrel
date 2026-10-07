package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// What a block holds, the text of a sign for one. A bedrock server sends it with the chunk and when it changes
public class BlockEntityConverter {

    private static final int SIGN_LINES = 4;
    private static final int CAMPFIRE_SLOTS = 4;
    // The sides of a decorated pot, in the order a bedrock server lists them
    private static final String[] POT_SIDES = {"back", "left", "right", "front"};
    // How many java block states lie between a block and the same block of the next color, and between a lectern
    // and the same lectern with a book
    private static final int BED_STATES = 16;
    private static final int STANDING_BANNER_STATES = 16;
    private static final int WALL_BANNER_STATES = 4;
    private static final int LECTERN_BOOK_STATES = 2;
    private static final int HEAD_ROTATIONS = 16;
    private static final int RED = 14;
    private static final int OMINOUS_BANNER = 1;
    // The banner of a raid is not told pattern by pattern: these are its patterns, each followed by its color
    private static final String[] OMINOUS_PATTERNS = {
            "rhombus", "cyan", "stripe_bottom", "light_gray", "stripe_center", "gray", "border", "light_gray",
            "stripe_middle", "black", "half_horizontal", "light_gray", "circle", "light_gray", "border", "black"
    };
    // The colors a sign can be written in, a java client has them by name and a bedrock server as the color itself
    private static final String[] DYE_NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] DYE_COLORS = {0xFFFFFF, 0xFF681F, 0xFF00FF, 0x9AC0CD, 0xFFFF00, 0xBFFF00, 0xFF69B4, 0x808080, 0xD3D3D3, 0x00FFFF, 0xA020F0, 0x0000FF, 0x8B4513, 0x00FF00, 0xFF0000, 0x000000};

    public static boolean isTranslated(BlockEntityType type) {
        return type == BlockEntityType.SIGN || type == BlockEntityType.HANGING_SIGN || type == BlockEntityType.BANNER || type == BlockEntityType.MOB_SPAWNER
                || type == BlockEntityType.CAMPFIRE || type == BlockEntityType.SHELF || type == BlockEntityType.DECORATED_POT;
    }

    // The java block a bedrock block is with what it holds: a bed and a banner of the color that is told there, a
    // head that looks the way that is told there, a pot with its plant and a lectern with its book
    public static int getJavaBlock(int javaBlock, NbtMap bedrock) {
        if (bedrock == null) {
            return javaBlock;
        }

        if (BlockConverter.isJavaBed(javaBlock)) {
            // The colors of a bed are numbered as the java ones are, a bed that tells none is red
            return javaBlock + (bedrock.containsKey("color", NbtType.BYTE) ? bedrock.getByte("color") & 15 : RED) * BED_STATES;
        } else if (BlockConverter.isJavaStandingBanner(javaBlock)) {
            return javaBlock + getBannerColor(bedrock) * STANDING_BANNER_STATES;
        } else if (BlockConverter.isJavaWallBanner(javaBlock)) {
            return javaBlock + getBannerColor(bedrock) * WALL_BANNER_STATES;
        } else if (BlockConverter.isJavaFloorHead(javaBlock)) {
            // A java head looks one of 16 ways, a bedrock one is turned by degrees, or was told as the java one is
            float rotation = bedrock.containsKey("Rot", NbtType.BYTE) ? bedrock.getByte("Rot") * (360f / HEAD_ROTATIONS) : bedrock.getFloat("Rotation", 0);
            return javaBlock + (Math.round(rotation * HEAD_ROTATIONS / 360) % HEAD_ROTATIONS + HEAD_ROTATIONS) % HEAD_ROTATIONS;
        } else if (BlockConverter.isJavaChest(javaBlock)) {
            // Two chests are one large chest when each tells where its other half is
            if (bedrock.containsKey("pairx", NbtType.INT) && bedrock.containsKey("pairz", NbtType.INT)) {
                return BlockConverter.getJavaChest(javaBlock, bedrock.getInt("pairx") - bedrock.getInt("x"), bedrock.getInt("pairz") - bedrock.getInt("z"));
            }
            return javaBlock;
        } else if (BlockConverter.isJavaFlowerPot(javaBlock)) {
            Integer potted = BlockConverter.getJavaPottedPlant(bedrock.getCompound("PlantBlock").getString("name", ""));
            return potted == null ? javaBlock : potted;
        } else if (BlockConverter.isJavaLectern(javaBlock)) {
            return bedrock.getBoolean("hasBook", false) ? javaBlock - LECTERN_BOOK_STATES : javaBlock;
        }
        return javaBlock;
    }

    // The color of a banner as a java client numbers it. A bedrock server numbers the colors of a banner the other
    // way around, and the banner of a raid is a white one whatever it tells
    private static int getBannerColor(NbtMap bedrock) {
        return bedrock.getInt("Type", 0) == OMINOUS_BANNER ? 0 : DYE_NAMES.length - 1 - (bedrock.getInt("Base", 0) & 15);
    }

    // What the java client is told a block of this kind holds. For most kinds that is nothing, the client only has
    // to know that the block is there to draw it
    public static NbtMap bedrockToJava(BlockEntityType type, NbtMap bedrock) {
        if (bedrock == null || !isTranslated(type)) {
            return NbtMap.EMPTY;
        }
        if (type == BlockEntityType.BANNER) {
            return getBanner(bedrock);
        } else if (type == BlockEntityType.MOB_SPAWNER) {
            return getSpawner(bedrock);
        } else if (type == BlockEntityType.CAMPFIRE) {
            // A campfire has its four items each under a name of its own
            List<NbtMap> items = new ArrayList<>();
            for (int slot = 0; slot < CAMPFIRE_SLOTS; slot++) {
                items.add(bedrock.getCompound("Item" + (slot + 1)));
            }
            return getItems(items);
        } else if (type == BlockEntityType.SHELF) {
            return getItems(bedrock.getList("Items", NbtType.COMPOUND, new ArrayList<>()));
        } else if (type == BlockEntityType.DECORATED_POT) {
            return getDecoratedPot(bedrock);
        }

        NbtMapBuilder sign = NbtMap.builder();
        // Before signs had a back, the text was not in a part of its own
        sign.putCompound("front_text", getSignText(bedrock.containsKey("FrontText", NbtType.COMPOUND) ? bedrock.getCompound("FrontText") : bedrock));
        sign.putCompound("back_text", getSignText(bedrock.getCompound("BackText")));
        sign.putBoolean("is_waxed", bedrock.getBoolean("IsWaxed", false));
        return sign.build();
    }

    private static NbtMap getBanner(NbtMap bedrock) {
        List<NbtMap> patterns = new ArrayList<>();
        if (bedrock.getInt("Type", 0) == OMINOUS_BANNER) {
            for (int pattern = 0; pattern < OMINOUS_PATTERNS.length; pattern += 2) {
                patterns.add(NbtMap.builder().putString("pattern", "minecraft:" + OMINOUS_PATTERNS[pattern]).putString("color", OMINOUS_PATTERNS[pattern + 1]).build());
            }
        }
        for (NbtMap bedrockPattern : bedrock.getList("Patterns", NbtType.COMPOUND, new ArrayList<>())) {
            String pattern = BannerConverter.getJavaPattern(bedrockPattern.getString("Pattern", ""));
            if (pattern != null) {
                patterns.add(NbtMap.builder().putString("pattern", pattern).putString("color", DYE_NAMES[DYE_NAMES.length - 1 - (bedrockPattern.getInt("Color", 0) & 15)]).build());
            }
        }
        return NbtMap.builder().putList("patterns", NbtType.COMPOUND, patterns).build();
    }

    // The mob that turns in a spawner
    private static NbtMap getSpawner(NbtMap bedrock) {
        String bedrockEntity = bedrock.getString("EntityIdentifier", "");
        EntityType entityType = bedrockEntity.isEmpty() ? null : EntityConverter.bedrockToJavaEntityType(bedrockEntity);
        if (entityType == null) {
            return NbtMap.EMPTY;
        }
        NbtMap entity = NbtMap.builder().putString("id", "minecraft:" + entityType.name().toLowerCase(Locale.ROOT)).build();
        return NbtMap.builder().putCompound("SpawnData", NbtMap.builder().putCompound("entity", entity).build()).build();
    }

    // The items that lie on a campfire or a shelf, each with the place it lies in
    private static NbtMap getItems(List<NbtMap> bedrockItems) {
        List<NbtMap> items = new ArrayList<>();
        for (int slot = 0; slot < bedrockItems.size(); slot++) {
            NbtMap bedrockItem = bedrockItems.get(slot);
            String name = bedrockItem.getString("Name", "");
            String javaName = name.isEmpty() ? null : ItemConverter.getJavaItemName(name, bedrockItem.getShort("Damage", (short) 0));
            if (javaName != null && bedrockItem.getByte("Count", (byte) 1) > 0) {
                items.add(NbtMap.builder().putByte("Slot", (byte) slot).putString("id", javaName).putInt("count", bedrockItem.getByte("Count", (byte) 1)).build());
            }
        }
        return NbtMap.builder().putList("Items", NbtType.COMPOUND, items).build();
    }

    // What the four sides of a pot are made of, a side without a sherd is a brick
    // A bedrock pot lists what its four sides were made of, a brick for a side without a picture. A java pot has
    // the item of a side under the name of the side, and nothing for a side without a picture
    private static NbtMap getDecoratedPot(NbtMap bedrock) {
        List<String> bedrockSherds = bedrock.getList("sherds", NbtType.STRING, new ArrayList<>());
        NbtMapBuilder sherds = NbtMap.builder();
        for (int side = 0; side < POT_SIDES.length && side < bedrockSherds.size(); side++) {
            String sherd = bedrockSherds.get(side).isEmpty() ? null : ItemConverter.getJavaItemName(bedrockSherds.get(side), 0);
            if (sherd != null && !sherd.equals("minecraft:brick")) {
                sherds.putCompound(POT_SIDES[side], NbtMap.builder().putString("id", sherd).putInt("count", 1).build());
            }
        }
        return NbtMap.builder().putCompound("sherds", sherds.build()).build();
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
