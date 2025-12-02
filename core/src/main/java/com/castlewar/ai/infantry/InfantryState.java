package com.castlewar.ai.infantry;

/**
 * State enum for Infantry units - used for animation/UI output only.
 * <p>
 * The BehaviorTree is the sole decision maker; this enum simply reflects
 * the current high-level state for rendering purposes.
 */
public enum InfantryState {
    /** Standing idle, waiting for orders */
    IDLE,
    
    /** Moving in formation with squad */
    MARCH,
    
    /** Holding position in defensive formation */
    HOLD,
    
    /** Actively engaging enemy in melee combat */
    ENGAGE,
    
    /** Charging toward enemy */
    CHARGE,
    
    /** Retreating from combat */
    RETREAT,
    
    /** Regrouping with nearby allies */
    REGROUP
}
