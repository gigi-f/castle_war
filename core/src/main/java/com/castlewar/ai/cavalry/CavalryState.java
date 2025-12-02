package com.castlewar.ai.cavalry;

/**
 * FSM-style state enum for Cavalry units.
 * Used as output indicator only - BT is the sole decision maker.
 */
public enum CavalryState {
    /** Standing idle, horse at rest */
    IDLE,
    
    /** Moving at normal patrol speed */
    TROTTING,
    
    /** Moving quickly in formation */
    CANTERING,
    
    /** Full speed charge attack */
    CHARGING,
    
    /** Engaging enemy in melee combat */
    ENGAGING,
    
    /** Wheeling around for another charge */
    WHEELING,
    
    /** Falling back after failed charge */
    RETREATING,
    
    /** Horse has been killed, unit is dismounted */
    DISMOUNTED
}
