package org.barrelmc.barrel.player;

import lombok.Getter;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.network.translator.TranslatorUtils;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Who rides what. A bedrock server tells of every rider by itself, with the ids that stay with an entity. A java
// client is told all riders of a vehicle at once, and seats them itself. This belongs to the thread that translates
// the packets
//
// What a player steers, a boat or a horse, a server of mojang moves itself by the keys the player holds, and puts
// right whoever has it somewhere else. A java client moves what its player steers itself, by rules of its own: a
// boat is faster for it and turns faster. So the java client is kept from steering. Its player is seated on
// something that is not seen, the saddle, and that rides in the place of the player: with a rider that is no
// player, a java client leaves a boat or a horse to its server, and seats the player where it would sit anyway
public class Riding {

    // A bedrock server has a player that rides this far above where a java client has the place it is seated at:
    // the one tells where the eyes are, the other seats a player by a point a little above its feet
    private static final float JAVA_SEAT = Entity.PLAYER_EYE_HEIGHT - 0.6F;
    // The id the java client has the saddle by, which no entity of a server has
    private static final int SADDLE_ID = Integer.MAX_VALUE - 1;
    // For how long a server that was asked to let the player off is waited for
    private static final long LEAVING_MILLIS = 1000;

    private final Player player;
    // The riders of the vehicles, the one that steers first
    private final Map<Long, List<Long>> riders = new HashMap<>();
    // What the player itself rides, 0 for nothing
    @Getter
    private long vehicle;
    // Whether the java client has the saddle
    private boolean saddled;
    // Until when the player is said to be where it gets off to, see leave
    private long leavingUntil;

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
        boolean self = rider == this.player.getRuntimeEntityId();
        if (self) {
            this.vehicle = rides ? vehicle : 0;
            this.leavingUntil = 0;
            if (!rides) {
                this.removeSaddle();
            }
        }
        changed.forEach(this::sendRiders);
        if (self && !rides) {
            this.getOff(this.player.getEntities().get(vehicle));
        }
    }

    // A server of mojang leaves it to the client where a player stands once it got off, and a java client leaves it
    // to its server: seated, a java client has the feet of a player below the seat, which can be in the ground.
    // The player is put on top of what it rode, where a java server puts it when it finds no better place. A
    // server that puts the player somewhere itself tells so right after
    private void getOff(Entity vehicle) {
        if (vehicle != null) {
            this.player.setPosition(vehicle.x, vehicle.y + vehicle.getHeight(), vehicle.z);
            this.player.getInput().teleportJava();
        }
    }

    // An entity is gone, with it who rode it and what it rode
    public void remove(long runtimeEntityId, Entity entity) {
        this.riders.remove(runtimeEntityId);
        if (this.vehicle == runtimeEntityId) {
            this.vehicle = 0;
            this.leavingUntil = 0;
            this.removeSaddle();
            this.getOff(entity);
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
        this.leavingUntil = 0;
        // Gone for the java client with everything else
        this.saddled = false;
    }

    // Where the server has the eyes of the player while it rides, or is to have them while the player gets off.
    // Null when the player does not ride or what it rides is not known
    public Vector3f getSeat() {
        Entity vehicle = this.vehicle == 0 ? null : this.player.getEntities().get(this.vehicle);
        if (vehicle == null) {
            return null;
        }
        if (System.currentTimeMillis() < this.leavingUntil) {
            return Vector3f.from(vehicle.x, vehicle.y + vehicle.getHeight() + Entity.PLAYER_EYE_HEIGHT, vehicle.z);
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

    // What a bedrock client tells the server when its player wants to get off what it rides. A bedrock client
    // has its player off at once, and a server of mojang takes the player to be where the client says next. So
    // from now on the player is said to be where it gets off to, see getOff, and not on its seat anymore: the feet
    // of a player that is seated can be in the ground, which it then falls through. The java client is taken off
    // when the server tells that the player got off
    public void leave() {
        if (this.vehicle != 0) {
            InteractPacket interactPacket = new InteractPacket();
            interactPacket.setAction(InteractPacket.Action.LEAVE_VEHICLE);
            interactPacket.setRuntimeEntityId(this.vehicle);
            this.player.getBedrockSession().sendPacket(interactPacket);
            this.leavingUntil = System.currentTimeMillis() + LEAVING_MILLIS;
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

        // What stands in for a seat is not steered by a java client, it needs no saddle
        Entity vehicle = this.player.getEntities().get(vehicleId);
        boolean saddle = vehicleId == this.vehicle && vehicle != null && !vehicle.isStandIn();
        if (saddle) {
            if (!this.saddled) {
                this.saddled = true;
                this.player.getJavaSession().send(new ClientboundAddEntityPacket(SADDLE_ID, UUID.randomUUID(), EntityType.ITEM_DISPLAY, vehicle.x, vehicle.y + vehicle.getShownOffset(), vehicle.z, Vector3d.ZERO, 0, 0, 0));
            }
            for (int i = 0; i < javaRiders.length; i++) {
                if (javaRiders[i] == (int) this.player.getRuntimeEntityId()) {
                    javaRiders[i] = SADDLE_ID;
                }
            }
        } else if (vehicleId == this.vehicle) {
            this.removeSaddle();
        }
        this.player.getJavaSession().send(new ClientboundSetPassengersPacket((int) vehicleId, javaRiders));
        if (saddle) {
            this.player.getJavaSession().send(new ClientboundSetPassengersPacket(SADDLE_ID, new int[]{(int) this.player.getRuntimeEntityId()}));
        }
        if (ridersOfVehicle.isEmpty()) {
            this.riders.remove(vehicleId);
        }
    }

    // A java client takes whoever rode something off it when that is gone
    private void removeSaddle() {
        if (this.saddled) {
            this.saddled = false;
            this.player.getJavaSession().send(new ClientboundRemoveEntitiesPacket(new int[]{SADDLE_ID}));
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
