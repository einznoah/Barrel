package org.barrelmc.barrel.player;

import lombok.Getter;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Who rides what. A bedrock server tells of every rider by itself, with the ids that stay with an entity. A java
// client is told all riders of a vehicle at once, and seats them itself. This belongs to the thread that translates
// the packets
public class Riding {

    // A bedrock server has a player that rides this far above where a java client has the place it is seated at:
    // the one tells where the eyes are, the other seats a player by a point a little above its feet
    private static final float JAVA_SEAT = Entity.PLAYER_EYE_HEIGHT - 0.6F;

    private final Player player;
    // The riders of the vehicles, the one that steers first
    private final Map<Long, List<Long>> riders = new HashMap<>();
    // What the player itself rides, 0 for nothing
    @Getter
    private long vehicle;

    public Riding(Player player) {
        this.player = player;
    }

    public boolean isRiding() {
        return this.vehicle != 0;
    }

    // The server tells that an entity rides another one from now on, or no longer does
    public void link(EntityLinkData link) {
        Long vehicle = this.getRuntimeId(link.getFrom()), rider = this.getRuntimeId(link.getTo());
        if (vehicle == null || rider == null) {
            // Told before the entity was, it is told again with the entity
            return;
        }

        // A rider is on one vehicle at a time
        List<Long> changed = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : this.riders.entrySet()) {
            if (entry.getValue().remove(rider)) {
                changed.add(entry.getKey());
            }
        }
        boolean rides = link.getType() != EntityLinkData.Type.REMOVE;
        if (rides) {
            List<Long> ridersOfVehicle = this.riders.computeIfAbsent(vehicle, id -> new ArrayList<>());
            if (link.getType() == EntityLinkData.Type.RIDER) {
                ridersOfVehicle.add(0, rider);
            } else {
                ridersOfVehicle.add(rider);
            }
            this.seat(vehicle, rider);
        }
        if (!changed.contains(vehicle)) {
            changed.add(vehicle);
        }
        if (rider == this.player.getRuntimeEntityId()) {
            this.vehicle = rides ? vehicle : 0;
        }
        changed.forEach(this::sendRiders);
    }

    // An entity is gone, with it who rode it and what it rode
    public void remove(long runtimeEntityId) {
        this.riders.remove(runtimeEntityId);
        if (this.vehicle == runtimeEntityId) {
            this.vehicle = 0;
        }
        List<Long> changed = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : this.riders.entrySet()) {
            if (entry.getValue().remove((Long) runtimeEntityId)) {
                changed.add(entry.getKey());
            }
        }
        changed.forEach(this::sendRiders);
    }

    // The entities of a dimension are gone when the player leaves it
    public void clear() {
        this.riders.clear();
        this.vehicle = 0;
    }

    // Where the server has the eyes of the player while it rides, null when it does not or what it rides is not known
    public Vector3f getSeat() {
        Entity vehicle = this.vehicle == 0 ? null : this.player.getEntities().get(this.vehicle);
        if (vehicle == null) {
            return null;
        }
        Vector3f seat = this.player.getSelf().getSeatOffset();
        if (seat == null) {
            return Vector3f.from(vehicle.x, vehicle.y + JAVA_SEAT, vehicle.z);
        }
        // The seat is told as seen from the vehicle, which looks some way
        double yaw = Math.toRadians(vehicle.yaw);
        double x = seat.getX() * Math.cos(yaw) - seat.getZ() * Math.sin(yaw), z = seat.getX() * Math.sin(yaw) + seat.getZ() * Math.cos(yaw);
        return Vector3f.from(vehicle.x + x, vehicle.y + seat.getY(), vehicle.z + z);
    }

    // The server told where on its vehicle an entity is seated
    public void seatChanged(Entity rider) {
        long riderId = rider == this.player.getSelf() ? this.player.getRuntimeEntityId() : this.getRuntimeId(rider);
        for (Map.Entry<Long, List<Long>> entry : this.riders.entrySet()) {
            if (entry.getValue().contains(riderId)) {
                this.seat(entry.getKey(), riderId);
            }
        }
    }

    // What a bedrock client tells the server when its player wants to get off what it rides
    public void leave() {
        if (this.vehicle != 0) {
            InteractPacket interactPacket = new InteractPacket();
            interactPacket.setAction(InteractPacket.Action.LEAVE_VEHICLE);
            interactPacket.setRuntimeEntityId(this.vehicle);
            this.player.getBedrockSession().sendPacket(interactPacket);
        }
    }

    // What stands in for an entity a java client has no kind for has no seat of its own: it is put where a java
    // client seats a player as high as the server has it
    private void seat(long vehicleId, long riderId) {
        Entity vehicle = this.player.getEntities().get(vehicleId);
        Entity rider = riderId == this.player.getRuntimeEntityId() ? this.player.getSelf() : this.player.getEntities().get(riderId);
        if (vehicle == null || rider == null || !vehicle.isStandIn() || !rider.isPlayer() || rider.getSeatOffset() == null) {
            return;
        }
        float shownOffset = rider.getSeatOffset().getY() - JAVA_SEAT;
        if (shownOffset != vehicle.getShownOffset()) {
            vehicle.setShownOffset(shownOffset);
            TranslatorUtils.sendEntityPosition(this.player, vehicleId, vehicle, false);
        }
    }

    private void sendRiders(long vehicleId) {
        if (vehicleId != this.player.getRuntimeEntityId() && !this.player.getEntities().containsKey(vehicleId)) {
            return;
        }
        List<Long> ridersOfVehicle = this.riders.getOrDefault(vehicleId, List.of());
        int[] javaRiders = ridersOfVehicle.stream().filter(rider -> rider == this.player.getRuntimeEntityId() || this.player.getEntities().containsKey(rider)).mapToInt(Long::intValue).toArray();
        this.player.getJavaSession().send(new ClientboundSetPassengersPacket((int) vehicleId, javaRiders));
        if (ridersOfVehicle.isEmpty()) {
            this.riders.remove(vehicleId);
        }
    }

    // Links name entities by the id that stays with them, everything else by the one of this session
    private Long getRuntimeId(long uniqueEntityId) {
        if (uniqueEntityId == this.player.getUniqueEntityId()) {
            return this.player.getRuntimeEntityId();
        }
        Long runtimeEntityId = this.player.getEntityRuntimeIds().get(uniqueEntityId);
        // Not every server has two ids for an entity
        return runtimeEntityId != null ? runtimeEntityId : this.player.getEntities().containsKey(uniqueEntityId) ? (Long) uniqueEntityId : null;
    }

    private long getRuntimeId(Entity entity) {
        for (Map.Entry<Long, Entity> entry : this.player.getEntities().entrySet()) {
            if (entry.getValue() == entity) {
                return entry.getKey();
            }
        }
        return 0;
    }
}
