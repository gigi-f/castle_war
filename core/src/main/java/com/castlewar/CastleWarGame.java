package com.castlewar;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.castlewar.screens.DualViewScreen;
import com.castlewar.simulation.SimulationConfig;
import com.castlewar.world.GridWorld;

/**
 * Main game class that bootstraps the Castle War 2D grid simulation.
 */
public class CastleWarGame extends Game {
    private SimulationConfig config;
    private GridWorld gridWorld;
    private DualViewScreen dualViewScreen;

    @Override
    public void create() {
        Gdx.app.log("CastleWarGame", "Initializing 2D grid simulation...");
        
        // Initialize configuration
        config = new SimulationConfig();
    config.setWorldWidth(80);   // 80 blocks wide
    config.setWorldDepth(48);   // 48 blocks deep
    config.setWorldHeight(32);  // 32 blocks tall
        
        // Create grid world with flat terrain
        gridWorld = new GridWorld(
            (int) config.getWorldWidth(),
            (int) config.getWorldDepth(),
            (int) config.getWorldHeight()
        );
        
        // Set up the dual-view screen
        dualViewScreen = new DualViewScreen(config, gridWorld);
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
}
