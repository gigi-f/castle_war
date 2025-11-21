package com.castlewar.simulation;

import com.castlewar.entity.Assassin;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;
import com.castlewar.world.GridWorld;

import java.util.List;

/**
 * System responsible for updating AI behavior and entity scanning.
 */
public class AISystem implements SimulationSystem {
    private final List<Entity> entities;
    private final GridWorld gridWorld;

    public AISystem(List<Entity> entities, GridWorld gridWorld) {
        this.entities = entities;
        this.gridWorld = gridWorld;
    }

    @Override
    public void update(float delta) {
        // Update AI and entity behaviors
        for (Entity entity : entities) {
            if (entity instanceof Unit) {
                Unit unit = (Unit) entity;
                if (!unit.isCorpse()) {
                    unit.scanForEnemies(entities, gridWorld);
                }
            }
            if (entity instanceof Assassin) {
                Assassin assassin = (Assassin) entity;
                if (!assassin.isCorpse()) {
                    assassin.checkForGuards(entities, gridWorld, delta);
                }
            }
            entity.update(delta, gridWorld);
        }
    }
}
