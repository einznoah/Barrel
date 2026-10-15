package org.barrelmc.barrel.player;

import net.kyori.adventure.key.Key;
import org.barrelmc.barrel.entity.Entity;
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
// shown, its color. A java client is told the waypoints that are shown in its dimension, and works out itself how
// far and in which direction each one is. This belongs to the thread that translates the packets
public class Waypoints {

    private static final Key STYLE = Key.key("minecraft:default");

    private static final class Waypoint {
        private boolean visible = true;
        private Vector3i position;
        private int dimension;
        private Integer color;
        // Whether the java client was told of it
        private boolean tracked;
    }

    private final Player player;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();

    public Waypoints(Player player) {
        this.player = player;
    }

    public void update(LocatorBarPacket.Payload payload) {
        UUID id = payload.getGroupHandle();
        if (id == null) {
            return;
        }
        if (payload.getActionFlag() == LocatorBarPacket.Action.REMOVE) {
            Waypoint waypoint = this.waypoints.remove(id);
            if (waypoint != null && waypoint.tracked) {
                this.send(WaypointOperation.UNTRACK, id, waypoint);
            }
            return;
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
        }
        this.show(id, waypoint, moved);
    }

    private void show(UUID id, Waypoint waypoint, boolean moved) {
        boolean shown = waypoint.visible && waypoint.position != null && waypoint.dimension == this.player.getDimension();
        if (shown && !waypoint.tracked) {
            waypoint.tracked = true;
            this.send(WaypointOperation.TRACK, id, waypoint);
        } else if (shown && moved) {
            this.send(WaypointOperation.UPDATE, id, waypoint);
        } else if (!shown && waypoint.tracked) {
            waypoint.tracked = false;
            this.send(WaypointOperation.UNTRACK, id, waypoint);
        }
    }

    // The player came into another dimension. A java client keeps its waypoints, those of the dimension it left go
    public void dimensionChanged() {
        for (Map.Entry<UUID, Waypoint> waypoint : this.waypoints.entrySet()) {
            this.show(waypoint.getKey(), waypoint.getValue(), false);
        }
    }

    private void send(WaypointOperation operation, UUID id, Waypoint waypoint) {
        TrackedWaypoint.Icon icon = new TrackedWaypoint.Icon(STYLE, Optional.ofNullable(waypoint.color));
        TrackedWaypoint tracked = new TrackedWaypoint(id, null, icon, TrackedWaypoint.Type.VEC3I,
                new Vec3iWaypointData(waypoint.position == null ? Vector3i.ZERO : waypoint.position));
        this.player.getJavaSession().send(new ClientboundTrackedWaypointPacket(operation, tracked));
    }
}
