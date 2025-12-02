package com.castlewar.entity;

/**
 * Team affiliation for units in the Castle War simulation.
 * <p>
 * Two teams battle for control - WHITE and BLACK - each defending
 * their castle and attempting to eliminate the enemy king.
 */
public enum Team {
    WHITE,
    BLACK;
    
    /**
     * Gets the opposing team.
     * 
     * @return WHITE if this is BLACK, BLACK if this is WHITE
     */
    public Team getOpposing() {
        return this == WHITE ? BLACK : WHITE;
    }
    
    /**
     * Checks if this team is hostile to another.
     * 
     * @param other The other team
     * @return true if teams are different (hostile)
     */
    public boolean isHostileTo(Team other) {
        return other != null && other != this;
    }
    
    /**
     * Checks if this team is allied with another.
     * 
     * @param other The other team
     * @return true if teams are the same (allied)
     */
    public boolean isAlliedWith(Team other) {
        return other == this;
    }
}
