/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network.translator;

import net.kyori.adventure.key.Key;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.converter.EntityDataConverter;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslatorUtils {

    private static final double PLAYER_MOVEMENT_SPEED = 0.1F;
    private static final Pattern TEXT_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    // What a server answers most often to a command that went wrong, which the java client has no text for
    private static final Map<String, String> TEXT_FALLBACKS = Map.ofEntries(
            Map.entry("commands.generic.unknown", "Unknown command: %s"),
            Map.entry("commands.generic.syntax", "Syntax error: unexpected \"%2$s\" at \"%1$s>>%2$s<<%3$s\""),
            Map.entry("commands.generic.noTargetMatch", "No targets matched selector"),
            Map.entry("commands.generic.player.notFound", "That player cannot be found"),
            Map.entry("commands.generic.usage", "Usage: %s"),
            Map.entry("tile.bed.noSleep", "You can only sleep at night and during thunderstorms"),
            Map.entry("tile.bed.notSafe", "You may not rest now, there are monsters nearby"),
            Map.entry("tile.bed.occupied", "This bed is occupied"),
            Map.entry("tile.bed.tooFar", "Bed is too far away"),
            Map.entry("tile.bed.obstructed", "This bed is obstructed"),
            Map.entry("tile.bed.respawnSet", "Respawn point set"),
            Map.entry("tile.bed.notValid", "Your home bed was missing or obstructed"),
            Map.entry("chat.type.sleeping", "%s is sleeping in a bed. To skip to dawn, %s more players need to sleep in a bed at the same time.")
    );
    // The keys of the texts a java client has under another key
    private static final Map<String, String> JAVA_TEXT_KEYS = Map.of(
            "tile.bed.noSleep", "block.minecraft.bed.no_sleep",
            "tile.bed.notSafe", "block.minecraft.bed.not_safe",
            "tile.bed.occupied", "block.minecraft.bed.occupied",
            "tile.bed.tooFar", "block.minecraft.bed.too_far_away",
            "tile.bed.obstructed", "block.minecraft.bed.obstructed",
            "tile.bed.respawnSet", "block.minecraft.set_spawn",
            "tile.bed.notValid", "block.minecraft.spawn.not_valid"
    );
    // What a bed tells is shown above the hotbar by a java client
    private static final List<String> JAVA_OVERLAY_TEXTS = List.of("tile.bed.");
    // The key of the name of a mob, an item or a block: entity.zombie.name
    private static final Pattern BEDROCK_NAME_KEY = Pattern.compile("(entity|item|tile)\\.([a-z0-9_]+)\\.name");
    private static final String RAW_TEXT = "rawtext";
    // The colors of a text, by the number that stands for them in a text of the game
    private static final NamedTextColor[] TEXT_COLORS = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
    };

    // A bedrock server sends many texts as the key of a text of the bedrock client, with what goes into it: what a
    // command led to, that a player joined, how a player died. The proxy does not have those texts. The java client
    // has most of them under the same key, in the language of the player. For a key it does not have it shows the
    // key with what goes into it
    // A bedrock server sends some of its texts as a list of parts, each a text as it is or the key of a text with
    // what goes into it: {"rawtext":[{"text":"..."},{"translate":"...","with":["..."]}]}. What a command tells a
    // player with is sent like this, and that a bed cannot be slept in. Null for a text that is not one of these
    public static Component translateRawText(String message) {
        if (!message.startsWith("{")) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(message);
            if (root.isJsonObject() && root.getAsJsonObject().get(RAW_TEXT) instanceof JsonArray parts) {
                return translateRawText(parts);
            }
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            // Not made as expected, it is shown as it came
        }
        return null;
    }

    private static Component translateRawText(JsonArray parts) {
        TextComponent.Builder text = Component.text();
        for (JsonElement element : parts) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            if (part.has("translate")) {
                // What goes into the text is a list of texts, or parts as these are
                List<String> parameters = new ArrayList<>();
                JsonElement with = part.get("with");
                if (with instanceof JsonArray texts) {
                    for (JsonElement parameter : texts) {
                        parameters.add(parameter.getAsString());
                    }
                } else if (with instanceof JsonObject rawText && rawText.get(RAW_TEXT) instanceof JsonArray withParts) {
                    for (JsonElement withPart : withParts) {
                        JsonObject parameter = withPart.getAsJsonObject();
                        parameters.add(parameter.has("translate") ? "%" + parameter.get("translate").getAsString() : parameter.has("text") ? parameter.get("text").getAsString() : "");
                    }
                }
                text.append(translateText(part.get("translate").getAsString(), parameters));
            } else if (part.has("text")) {
                text.append(Component.text(part.get("text").getAsString()));
            } else if (part.get(RAW_TEXT) instanceof JsonArray inner) {
                text.append(translateRawText(inner));
            } else if (part.has("selector")) {
                // The server puts the names in before it sends the text, this is what is left if it does not
                text.append(Component.text(part.get("selector").getAsString()));
            }
        }
        return text.build();
    }

    // Whether a java client shows the text above the hotbar and not in the chat, where a bedrock client has it
    public static boolean isJavaOverlayText(String message) {
        for (String key : JAVA_OVERLAY_TEXTS) {
            if (message.contains(key)) {
                return true;
            }
        }
        return false;
    }

    // A text by its key. Most keys are the same for a java client, which then shows the text in its own language.
    // The names of mobs, items and blocks are keyed another way there, and so are a few texts
    private static Component translateKey(String key, List<Component> arguments, String fallback) {
        String javaKey = JAVA_TEXT_KEYS.get(key);
        Matcher name = BEDROCK_NAME_KEY.matcher(key);
        if (javaKey == null && name.matches()) {
            // What the java client does not have under this name either is shown by the name itself
            StringBuilder shown = new StringBuilder();
            for (String word : name.group(2).split("_")) {
                shown.append(shown.isEmpty() ? "" : " ").append(word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
            return Component.translatable((name.group(1).equals("tile") ? "block" : name.group(1)) + ".minecraft." + name.group(2), shown.toString(), arguments);
        }
        return Component.translatable(javaKey == null ? key : javaKey, TEXT_FALLBACKS.getOrDefault(key, fallback), arguments);
    }

    public static Component translateText(String message, List<String> parameters) {
        // What the text looks like comes before it
        int start = 0;
        NamedTextColor color = null;
        while (start + 1 < message.length() && message.charAt(start) == '\u00a7') {
            int number = Character.digit(message.charAt(start + 1), 16);
            if (number != -1) {
                color = TEXT_COLORS[number];
            }
            start += 2;
        }

        String key = message.startsWith("%", start) ? message.substring(start + 1) : message.substring(start);
        if (!TEXT_KEY.matcher(key).matches()) {
            // Not a key but the text itself
            return Component.text(fillText(message, parameters));
        }

        List<Component> arguments = new ArrayList<>();
        StringBuilder fallback = new StringBuilder(key);
        for (String parameter : parameters) {
            fallback.append(arguments.isEmpty() ? ": %s" : ", %s");
            // What goes into a text can be the key of a text itself, the name of a game mode for one
            boolean isKey = parameter.startsWith("%") && TEXT_KEY.matcher(parameter.substring(1)).matches();
            arguments.add(isKey ? translateKey(parameter.substring(1), Collections.emptyList(), parameter.substring(1)) : Component.text(parameter));
        }
        Component text = translateKey(key, arguments, fallback.toString());
        return color == null ? text : text.color(color);
    }

    // Puts what goes into a text at the places the text has for it, %s and %d in their order and %1$s by its number
    private static String fillText(String text, List<String> parameters) {
        StringBuilder filled = new StringBuilder();
        int next = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character != '%' || i + 1 >= text.length()) {
                filled.append(character);
                continue;
            }

            int end = i + 1;
            int number = 0;
            while (end < text.length() && Character.isDigit(text.charAt(end))) {
                number = number * 10 + text.charAt(end) - '0';
                end++;
            }
            boolean numbered = end > i + 1 && end < text.length() && text.charAt(end) == '$';
            if (numbered) {
                end++;
            }
            if (end < text.length() && (text.charAt(end) == 's' || text.charAt(end) == 'd') && (numbered || end == i + 1)) {
                int index = numbered ? number - 1 : next++;
                filled.append(index >= 0 && index < parameters.size() ? parameters.get(index) : "");
                i = end;
            } else {
                filled.append(character);
            }
        }
        return filled.toString();
    }

    public static GameMode translateGamemodeToJE(GameType gameType) {
        switch (gameType) {
            case CREATIVE:
                return GameMode.CREATIVE;
            case ADVENTURE:
                return GameMode.ADVENTURE;
            case SURVIVAL_VIEWER:
            case CREATIVE_VIEWER:
            case SPECTATOR:
                return GameMode.SPECTATOR;
            default:
                // DEFAULT is the game mode of the world, the player knows which that is
                return GameMode.SURVIVAL;
        }
    }

    // A java client is told how the speed of the player is made up. That of a bedrock client is a number that has
    // sprinting in it, so it is made up again from the effects the player has
    public static void sendMovementSpeed(Player player) {
        List<AttributeModifier> modifiers = new ArrayList<>();
        Integer speed = player.getEffects().get(Effect.SPEED);
        if (speed != null) {
            modifiers.add(new AttributeModifier(Key.key("effect.speed"), 0.2 * (speed + 1), ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }
        Integer slowness = player.getEffects().get(Effect.SLOWNESS);
        if (slowness != null) {
            modifiers.add(new AttributeModifier(Key.key("effect.slowness"), -0.15 * (slowness + 1), ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }
        if (player.isSprinting()) {
            // The client takes this off itself when it stops sprinting
            modifiers.add(new AttributeModifier(Key.key("sprinting"), 0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL));
        }

        Attribute movementSpeed = new Attribute(AttributeType.Builtin.MOVEMENT_SPEED, PLAYER_MOVEMENT_SPEED, modifiers);
        player.getJavaSession().send(new ClientboundUpdateAttributesPacket((int) player.getRuntimeEntityId(), Collections.singletonList(movementSpeed)));
    }

    public static void sendEntityData(Player player, long runtimeEntityId, Entity entity, EntityDataMap entityData) {
        Float height = entityData.get(EntityDataTypes.HEIGHT);
        if (height != null) {
            entity.setHeight(height);
        }
        Vector3f seatOffset = entityData.get(EntityDataTypes.SEAT_OFFSET);
        if (seatOffset != null) {
            entity.setSeatOffset(seatOffset);
            player.getRiding().seatChanged(entity);
        }
        // What stands in for an entity a java client has no kind for has none of what that entity is told with
        if (entity.isStandIn()) {
            return;
        }

        EntityMetadata<?, ?>[] javaEntityData = EntityDataConverter.bedrockToJavaEntityData(entity, entityData, entity == player.getSelf() ? player : null);
        if (javaEntityData.length > 0) {
            player.getJavaSession().send(new ClientboundSetEntityDataPacket((int) runtimeEntityId, javaEntityData));
        }
    }

    public static void sendEntityPosition(Player player, long runtimeEntityId, Entity entity, boolean onGround) {
        player.getJavaSession().send(new ClientboundTeleportEntityPacket((int) runtimeEntityId, Vector3d.from(entity.x, entity.y + entity.getShownOffset(), entity.z), Vector3d.ZERO, entity.yaw, entity.pitch, Collections.emptyList(), onGround));
        player.getJavaSession().send(new ClientboundRotateHeadPacket((int) runtimeEntityId, entity.getHeadYaw()));
    }
}
