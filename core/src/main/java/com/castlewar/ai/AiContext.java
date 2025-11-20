package com.castlewar.ai;

import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;
import java.util.List;
import com.castlewar.entity.Entity;

/**
 * Shared context object that exposes simulation-level data to AI agents.
 * It is intentionally lightweight so we can thread it through without
 * forcing entities to depend directly on WorldContext.
 */
public class AiContext {
    private WorldContext worldContext;

    public AiContext() {
    }

    public AiContext(WorldContext worldContext) {
        this.worldContext = worldContext;
    }

    public void attach(WorldContext worldContext) {
        this.worldContext = worldContext;
    }

    public WorldContext getWorldContext() {
        return worldContext;
    }

    public GridWorld getGrid() {
        return worldContext != null ? worldContext.getGridWorld() : null;
    }

    public List<Entity> getEntities() {
        return worldContext != null ? worldContext.getEntities() : List.of();
    }
}
