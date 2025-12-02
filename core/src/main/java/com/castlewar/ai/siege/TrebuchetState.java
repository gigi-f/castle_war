package com.castlewar.ai.siege;

/**
 * FSM state enum for Trebuchet siege engine.
 * BT is the sole decision maker; state is output-only indicator.
 * 
 * States:
 * - IDLE: Stationary, no target acquired
 * - AIM: Adjusting trajectory for target
 * - FIRE: Launching projectile
 * - RELOAD: Loading ammunition (slow)
 * - RELOCATE: Moving to better firing position
 */
public enum TrebuchetState {
    IDLE,
    AIM,
    FIRE,
    RELOAD,
    RELOCATE
}
