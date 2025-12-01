package com.castlewar.ai;

import com.castlewar.simulation.WorldContext;
import com.castlewar.entity.Entity;
import java.util.List;

public class AiContext {
    private final WorldContext worldContext;

    public AiContext(WorldContext worldContext) {
        this.worldContext = worldContext;
    }

    public WorldContext getWorldContext() {
        return worldContext;
    }

    public List<Entity> getEntities() {
        return worldContext.getEntities();
    }
    
    public com.castlewar.world.GridWorld getGrid() {
        return worldContext == null ? null : worldContext.getGridWorld();
    }
}
