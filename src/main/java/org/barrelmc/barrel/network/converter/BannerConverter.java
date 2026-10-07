package org.barrelmc.barrel.network.converter;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.BannerPatternLayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BannerConverter {

    // A banner has no more patterns than this
    public static final int MAX_PATTERNS = 6;

    // The java names of the banner patterns, each followed by what bedrock calls it
    private static final String[] PATTERNS = {
            "base", "b", "square_bottom_left", "bl", "square_bottom_right", "br", "square_top_left", "tl", "square_top_right", "tr",
            "stripe_bottom", "bs", "stripe_top", "ts", "stripe_left", "ls", "stripe_right", "rs", "stripe_center", "cs",
            "stripe_middle", "ms", "stripe_downright", "drs", "stripe_downleft", "dls", "small_stripes", "ss", "cross", "cr",
            "straight_cross", "sc", "triangle_bottom", "bt", "triangle_top", "tt", "triangles_bottom", "bts", "triangles_top", "tts",
            "diagonal_left", "ld", "diagonal_up_right", "rd", "diagonal_up_left", "lud", "diagonal_right", "rud", "circle", "mc",
            "rhombus", "mr", "half_vertical", "vh", "half_horizontal", "hh", "half_vertical_right", "vhr", "half_horizontal_bottom", "hhb",
            "border", "bo", "curly_border", "cbo", "gradient", "gra", "gradient_up", "gru", "bricks", "bri", "globe", "glb",
            "creeper", "cre", "skull", "sku", "flower", "flo", "mojang", "moj", "piglin", "pig", "flow", "flw", "guster", "gus"
    };
    // The bedrock items that give a loom a pattern, each followed by that pattern
    private static final String[] PATTERN_ITEMS = {
            "creeper", "cre", "skull", "sku", "flower", "flo", "mojang", "moj", "field_masoned", "bri", "bordure_indented", "cbo",
            "piglin", "pig", "globe", "glb", "flow", "flw", "guster", "gus"
    };
    // The dyes in the order of the java colors, bedrock numbers the colors of a banner the other way around
    private static final List<String> DYES = Arrays.asList("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final Map<String, Integer> JAVA_PATTERN_IDS = new HashMap<>();
    // The patterns a loom offers without a pattern item, in the order a java client lists them
    private static final List<String> LOOM_PATTERNS = new ArrayList<>();

    // The java name of a pattern as a bedrock server calls it, null for one that is not known
    public static String getJavaPattern(String bedrockPattern) {
        for (int pattern = 0; pattern < PATTERNS.length; pattern += 2) {
            if (PATTERNS[pattern + 1].equals(bedrockPattern)) {
                return "minecraft:" + PATTERNS[pattern];
            }
        }
        return null;
    }

    public static void init() {
        Map<String, String> bedrockPatterns = new HashMap<>();
        for (int pattern = 0; pattern < PATTERNS.length; pattern += 2) {
            int javaPatternId = JavaRegistries.getId(JavaRegistries.BANNER_PATTERN, "minecraft:" + PATTERNS[pattern]);
            if (javaPatternId != -1) {
                JAVA_PATTERN_IDS.put(PATTERNS[pattern + 1], javaPatternId);
                bedrockPatterns.put("minecraft:" + PATTERNS[pattern], PATTERNS[pattern + 1]);
            }
        }

        for (int javaPatternId : JavaRegistries.getTag(JavaRegistries.BANNER_PATTERN, "minecraft:no_item_required")) {
            // Kept even if bedrock does not have it, the client counts it when it tells which one was picked
            LOOM_PATTERNS.add(bedrockPatterns.get(JavaRegistries.getName(JavaRegistries.BANNER_PATTERN, javaPatternId)));
        }
    }

    // The patterns a loom offers with this in its pattern slot. One bedrock does not have is null
    public static List<String> getLoomPatterns(ItemData patternItem) {
        if (ItemConverter.isEmpty(patternItem)) {
            return LOOM_PATTERNS;
        }

        for (int item = 0; item < PATTERN_ITEMS.length; item += 2) {
            if (patternItem.getDefinition().getIdentifier().equals("minecraft:" + PATTERN_ITEMS[item] + "_banner_pattern")) {
                return List.of(PATTERN_ITEMS[item + 1]);
            }
        }
        return List.of();
    }

    // Returns the color a dye gives a pattern, -1 for what is not a dye
    public static int getDyeColor(ItemData dye) {
        String bedrockName = ItemConverter.isEmpty(dye) ? "" : dye.getDefinition().getIdentifier();
        int javaColor = bedrockName.endsWith("_dye") ? DYES.indexOf(bedrockName.substring("minecraft:".length(), bedrockName.length() - "_dye".length())) : -1;
        return javaColor == -1 ? -1 : DYES.size() - 1 - javaColor;
    }

    public static boolean isBanner(ItemData item) {
        return !ItemConverter.isEmpty(item) && item.getDefinition().getIdentifier().equals("minecraft:banner");
    }

    private static List<NbtMap> getPatterns(NbtMap tag) {
        return tag == null ? new ArrayList<>() : new ArrayList<>(tag.getList("Patterns", NbtType.COMPOUND, new ArrayList<>()));
    }

    public static int getPatternCount(ItemData banner) {
        return getPatterns(banner.getTag()).size();
    }

    public static ItemData addPattern(ItemData banner, String bedrockPattern, int bedrockColor) {
        List<NbtMap> patterns = getPatterns(banner.getTag());
        patterns.add(NbtMap.builder().putInt("Color", bedrockColor).putString("Pattern", bedrockPattern).build());
        NbtMap tag = banner.getTag() == null ? NbtMap.EMPTY : banner.getTag();
        return banner.toBuilder().tag(tag.toBuilder().putList("Patterns", NbtType.COMPOUND, patterns).build()).build();
    }

    public static List<BannerPatternLayer> bedrockToJavaPatterns(NbtMap tag) {
        List<BannerPatternLayer> javaPatterns = new ArrayList<>();
        for (NbtMap pattern : getPatterns(tag)) {
            Integer javaPatternId = JAVA_PATTERN_IDS.get(pattern.getString("Pattern"));
            if (javaPatternId != null) {
                javaPatterns.add(new BannerPatternLayer(Holder.ofId(javaPatternId), DYES.size() - 1 - pattern.getInt("Color")));
            }
        }
        return javaPatterns;
    }
}
