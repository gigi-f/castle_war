package com.castlewar.ai.siege;

/**
 * FSM state enum for Battering Ram siege engine.
 * BT is the sole decision maker; state is output-only indicator.
 * 
 * States:
 * - IDLE: Stationary, no target
 * - ADVANCE: Moving toward gate/wall
 * - POSITION: Getting into ramming position
 * - WINDUP: Pulling back ram for swing
 * - STRIKE: Slamming ram into structure
 * - RETREAT: Moving away from danger
 */
public enum BatteringRamState {
    IDLE,
    ADVANCE,
    POSITION,
    WINDUP,
    STRIKE,
    RETREAT
}
