package com.castlewar.ai.archer;

/**
 * FSM-style state enum for Archer units.
 * Used as output indicator only - BT is the sole decision maker.
 */
public enum ArcherState {
    /** Standing idle, no threats detected */
    IDLE,
    
    /** Moving to a strategic position (high ground, cover) */
    REPOSITIONING,
    
    /** Aiming at a target, preparing to fire */
    AIMING,
    
    /** Actively firing arrows at enemies */
    FIRING,
    
    /** Kiting - moving away while maintaining attack capability */
    KITING,
    
    /** Taking cover behind obstacles */
    TAKING_COVER,
    
    /** Fleeing from melee threat */
    RETREATING
}
