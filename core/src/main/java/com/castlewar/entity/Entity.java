package com.castlewar.entity;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public abstract class Entity {
    protected final Vector3 position;
    protected final Vector3 velocity;
    protected final Team team;

    public Entity(float x, float y, float z, Team team) {
        this.position = new Vector3(x, y, z);
        this.velocity = new Vector3();
        this.team = team;
    }

    public abstract void update(float delta, GridWorld world);

    public float getX() { return position.x; }
    public float getY() { return position.y; }
    public float getZ() { return position.z; }
    public Team getTeam() { return team; }
}
