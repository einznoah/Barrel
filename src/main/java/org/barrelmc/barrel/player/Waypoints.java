package org.barrelmc.barrel.player;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.entity.Entity;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.TrackedWaypoint;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.Vec3iWaypointData;
import org.geysermc.mcprotocollib.protocol.data.game.level.waypoint.WaypointOperation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundTrackedWaypointPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// The dots of the locator bar, which show where the other players are. A server of mojang tells each waypoint
// once with all there is to it and then what changed of it: where it is and in which dimension, whether it is
// shown, its color. Where a player is that the client sees it does not tell: a bedrock client follows the player
// itself. A java client is told the waypoints that are shown in its dimension, under the id of the player as a
// java server does, and where the player is whenever it is at another block. Near that block it follows the player
// itself, and it works out how far and in which direction each one is. This belongs to the thread that translates
// the packets
public class Waypoints {

    private static final Key STYLE = Key.key("minecraft:default");

    private static final class Waypoint {
        private boolean visible = true;
        private Vector3i position;
        private int dimension;
        private Integer color;
        // The player it shows
        private Long entityUniqueId;
        // What the java client knows it by, while it was told of it
        private UUID javaId;
    }

    private final Player player;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();
    // The id of each player that is in the game, by the id of its entity
    private final Map<Long, UUID> playerIds = new HashMap<>();
    private boolean started;

    public Waypoints(Player player) {
        this.player = player;
    }

    public void playerAdded(long entityUniqueId, UUID id) {
        this.playerIds.put(entityUniqueId, id);
    }

    public void playerRemoved(UUID id) {
        this.playerIds.values().remove(id);
    }

    public void update(LocatorBarPacket.Payload payload) {
        UUID id = payload.getGroupHandle();
        if (id == null) {
            return;
        }
        if (payload.getActionFlag() == LocatorBarPacket.Action.REMOVE) {
            Waypoint waypoint = this.waypoints.remove(id);
            if (waypoint != null && waypoint.javaId != null) {
                this.send(WaypointOperation.UNTRACK, waypoint);
            }
            return;
        }
        if (!this.started) {
            this.started = true;
            this.player.runEveryTick(this::tick);
        }

        // What was not told is as it was
        Waypoint waypoint = this.waypoints.computeIfAbsent(id, key -> new Waypoint());
        LocatorBarWaypoint told = payload.getWaypoint();
        boolean moved = false;
        if (told != null) {
            if (told.getVisible() != null) {
                waypoint.visible = told.getVisible();
            }
            if (told.getWorldPosition() != null) {
                // The server tells where the eyes of a player are
                Vector3i position = told.getWorldPosition().getPosition().sub(0, Entity.PLAYER_EYE_HEIGHT, 0).floor().toInt();
                moved = !position.equals(waypoint.position) || told.getWorldPosition().getDimension() != waypoint.dimension;
                waypoint.position = position;
                waypoint.dimension = told.getWorldPosition().getDimension();
            }
            if (told.getColor() != null) {
                waypoint.color = told.getColor().getRGB() & 0xFFFFFF;
            }
            if (told.getEntityUniqueId() != null) {
                waypoint.entityUniqueId = told.getEntityUniqueId();
            }
        }
        this.show(id, waypoint, moved);
    }

    private void show(UUID id, Waypoint waypoint, boolean moved) {
        boolean shown = waypoint.visible && waypoint.position != null && waypoint.dimension == this.player.getDimension();
        if (shown && waypoint.javaId == null) {
            waypoint.javaId = waypoint.entityUniqueId == null ? id : this.playerIds.getOrDefault(waypoint.entityUniqueId, id);
            this.send(WaypointOperation.TRACK, waypoint);
        } else if (shown && moved) {
            this.send(WaypointOperation.UPDATE, waypoint);
        } else if (!shown && waypoint.javaId != null) {
            this.send(WaypointOperation.UNTRACK, waypoint);
            waypoint.javaId = null;
        }
    }

    // The players the client sees are where their entities are
    private void tick() {
        for (Waypoint waypoint : this.waypoints.values()) {
            if (waypoint.javaId == null || waypoint.entityUniqueId == null) {
                continue;
            }
            Long runtimeEntityId = this.player.getEntityRuntimeIds().get(waypoint.entityUniqueId);
            Entity entity = runtimeEntityId == null ? null : this.player.getEntities().get(runtimeEntityId);
            if (entity == null) {
                continue;
            }
            Vector3i position = Vector3i.from(GenericMath.floor(entity.getX()), GenericMath.floor(entity.getY()), GenericMath.floor(entity.getZ()));
            if (!position.equals(waypoint.position)) {
                waypoint.position = position;
                this.send(WaypointOperation.UPDATE, waypoint);
            }
        }
    }

    // The player came into another dimension. A java client keeps its waypoints, those of the dimension it left go
    public void dimensionChanged() {
        for (Map.Entry<UUID, Waypoint> waypoint : this.waypoints.entrySet()) {
            this.show(waypoint.getKey(), waypoint.getValue(), false);
        }
    }

    private void send(WaypointOperation operation, Waypoint waypoint) {
        TrackedWaypoint.Icon icon = new TrackedWaypoint.Icon(STYLE, Optional.ofNullable(waypoint.color));
        TrackedWaypoint tracked = new TrackedWaypoint(waypoint.javaId, null, icon, TrackedWaypoint.Type.VEC3I,
                new Vec3iWaypointData(waypoint.position == null ? Vector3i.ZERO : waypoint.position));
        this.player.getJavaSession().send(new ClientboundTrackedWaypointPacket(operation, tracked));
    }
}
