package com.castlewar;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.castlewar.screens.DualViewScreen;
import com.castlewar.simulation.SimulationConfig;
import com.castlewar.simulation.WorldContext;

/**
 * Main game class that bootstraps the Castle War 2D grid simulation.
 */
public class CastleWarGame extends Game {
    private final WorldContext worldContext;
    private final DualViewScreen.Options viewOptions;
    private DualViewScreen dualViewScreen;

    public CastleWarGame() {
        this(createDefaultContext(), DualViewScreen.Options.primaryWindow());
    }

    public CastleWarGame(WorldContext worldContext, DualViewScreen.Options viewOptions) {
        this.worldContext = worldContext;
        this.viewOptions = viewOptions;
    }

    @Override
    public void create() {
        Gdx.app.log("CastleWarGame", "Initializing 2D grid simulation...");

        dualViewScreen = new DualViewScreen(worldContext, viewOptions);
        setScreen(dualViewScreen);
        
        Gdx.app.log("CastleWarGame", "Initialization complete!");
    }

    @Override
    public void dispose() {
        if (dualViewScreen != null) {
            dualViewScreen.dispose();
        }
        super.dispose();
    }

    private static WorldContext createDefaultContext() {
        SimulationConfig config = new SimulationConfig();
        config.setWorldWidth(80);
        config.setWorldDepth(48);
        config.setWorldHeight(32);
        return new WorldContext(config);
    }
}
