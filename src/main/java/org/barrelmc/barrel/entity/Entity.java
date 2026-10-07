/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.entity;

import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.barrelmc.barrel.math.Vector3;

// An entity the java client was told about, with the position of its feet
public class Entity extends Vector3 {

    // The bedrock server sends the position of the eyes of a player
    public static final float PLAYER_EYE_HEIGHT = 1.62F;

    @Getter
    private final EntityType type;
    @Setter
    @Getter
    private float headYaw;

    // What the java client was told about the entity, of what is sent together with something else or only once
    @Setter
    @Getter
    private boolean effectParticles;
    @Setter
    @Getter
    private byte color;
    @Setter
    @Getter
    private boolean sheared;
    @Setter
    @Getter
    private int ownFlags;
    @Setter
    @Getter
    private boolean sleeping;
    @Setter
    @Getter
    private int handState;
    // The bed the server last told for the entity, a player lies in it while it sleeps
    @Setter
    @Getter
    private Vector3i bedPosition;
    // Where on a vehicle the server seats the entity, as seen from the vehicle. Told with the rider, null before
    @Setter
    @Getter
    private Vector3f seatOffset;
    // How high the server says the entity is
    @Setter
    @Getter
    private float height = 1;
    // Whether this stands in for an entity a java client has no kind for, and is not seen
    @Setter
    @Getter
    private boolean standIn;
    // How much higher the java client has the entity than the server: what stands in for a seat is put where a
    // java client seats a player as high as the server does
    @Setter
    @Getter
    private float shownOffset;

    public Entity(EntityType type) {
        this.type = type;
    }

    public boolean isPlayer() {
        return this.type == EntityType.PLAYER;
    }
}
