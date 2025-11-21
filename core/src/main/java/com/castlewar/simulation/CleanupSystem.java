package com.castlewar.simulation;

import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * System responsible for cleaning up expired entities.
 */
public class CleanupSystem implements SimulationSystem {
    private final List<Entity> entities;

    public CleanupSystem(List<Entity> entities) {
        this.entities = entities;
    }

    @Override
    public void update(float delta) {
        // Remove expired corpses
        entities.removeIf(e -> e instanceof Unit && ((Unit)e).shouldDespawn());
    }
}
