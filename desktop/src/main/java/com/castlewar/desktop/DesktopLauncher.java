package com.castlewar.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import com.castlewar.CastleWarGame;
import com.castlewar.screens.DualViewScreen;
import com.castlewar.simulation.SimulationConfig;
import com.castlewar.simulation.WorldContext;

/**
 * Desktop LWJGL3 launcher for the Castle War simulation.
 */
public class DesktopLauncher {
    public static void main(String[] arg) {
        SimulationConfig simConfig = new SimulationConfig();
        WorldContext sharedContext = new WorldContext(simConfig);

        Lwjgl3ApplicationConfiguration primaryConfig = new Lwjgl3ApplicationConfiguration();
        primaryConfig.setTitle("Castle War – Top View");
        primaryConfig.setWindowedMode(1280, 720);
        primaryConfig.setForegroundFPS(60);
        primaryConfig.useVsync(true);
        primaryConfig.setResizable(true);
        Lwjgl3Application app = new Lwjgl3Application(
            new CastleWarGame(sharedContext, DualViewScreen.Options.primaryWindow()),
            primaryConfig
        );


    }
}
