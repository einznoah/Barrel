/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.entity;

import lombok.Getter;
import lombok.Setter;
import org.barrelmc.barrel.math.Vector3;

// An entity the java client was told about, with the position of its feet
public class Entity extends Vector3 {

    // The bedrock server sends the position of the eyes of a player
    public static final float PLAYER_EYE_HEIGHT = 1.62F;

    @Getter
    private final boolean player;
    @Setter
    @Getter
    private float headYaw;

    public Entity(boolean player) {
        this.player = player;
    }
}
