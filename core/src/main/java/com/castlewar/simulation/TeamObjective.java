package com.castlewar.simulation;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Entity;
import com.castlewar.entity.King;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Manages team-wide objectives and provides objective data to AI agents.
 * <p>
 * The primary objective for each team is to <b>KILL THE ENEMY KING</b>.
 * This system:
 * <ul>
 *   <li>Tracks enemy king locations for each team</li>
 *   <li>Provides objective queries for AI behavior trees</li>
 *   <li>Broadcasts king death events to trigger morale effects</li>
 *   <li>Determines victory conditions</li>
 * </ul>
 * 
 * <h2>Usage in AI</h2>
 * AI agents can query the enemy king position to influence their behavior:
 * <pre>{@code
 * King enemyKing = TeamObjective.getInstance().getEnemyKing(unit.getTeam());
 * Vector3 kingPos = TeamObjective.getInstance().getEnemyKingPosition(unit.getTeam());
 * }</pre>
 */
public class TeamObjective implements SimulationSystem {
    
    private static TeamObjective instance;
    
    private King whiteKing;
    private King blackKing;
    private List<Entity> entities;
    
    private boolean whiteKingDead = false;
    private boolean blackKingDead = false;
    
    /** Cached position of white king for pathfinding */
    private final Vector3 whiteKingPosition = new Vector3();
    /** Cached position of black king for pathfinding */
    private final Vector3 blackKingPosition = new Vector3();
    
    private TeamObjective() {}
    
    public static TeamObjective getInstance() {
        if (instance == null) {
            instance = new TeamObjective();
        }
        return instance;
    }
    
    /**
     * Initializes the objective system with the entity list.
     * Call this after entities are spawned.
     */
    public void initialize(List<Entity> entities) {
        this.entities = entities;
        findKings();
    }
    
    /**
     * Locates the kings on both teams.
     */
    private void findKings() {
        whiteKing = null;
        blackKing = null;
        
        if (entities == null) return;
        
        for (Entity entity : entities) {
            if (entity instanceof King king) {
                if (king.getTeam() == Team.WHITE) {
                    whiteKing = king;
                } else if (king.getTeam() == Team.BLACK) {
                    blackKing = king;
                }
            }
        }
        
        // Update cached positions
        updateKingPositions();
        
        System.out.println("[TeamObjective] OBJECTIVE: Kill the enemy King!");
        System.out.println("[TeamObjective] WHITE King: " + (whiteKing != null ? whiteKing.getName() : "none"));
        System.out.println("[TeamObjective] BLACK King: " + (blackKing != null ? blackKing.getName() : "none"));
    }
    
    private void updateKingPositions() {
        if (whiteKing != null && !whiteKing.isCorpse()) {
            whiteKingPosition.set(whiteKing.getX(), whiteKing.getY(), whiteKing.getZ());
        }
        if (blackKing != null && !blackKing.isCorpse()) {
            blackKingPosition.set(blackKing.getX(), blackKing.getY(), blackKing.getZ());
        }
    }
    
    @Override
    public void update(float delta) {
        if (entities == null) return;
        
        // Re-find kings if needed
        if (whiteKing == null || blackKing == null) {
            findKings();
        }
        
        // Update king positions
        updateKingPositions();
        
        // Check for king deaths
        checkKingStatus();
    }
    
    /**
     * Checks if either king has died and triggers victory events.
     */
    private void checkKingStatus() {
        // Check white king
        if (whiteKing != null && whiteKing.isCorpse() && !whiteKingDead) {
            whiteKingDead = true;
            onKingDeath(Team.WHITE);
        }
        
        // Check black king
        if (blackKing != null && blackKing.isCorpse() && !blackKingDead) {
            blackKingDead = true;
            onKingDeath(Team.BLACK);
        }
    }
    
    /**
     * Called when a king dies - triggers victory for the opposing team.
     */
    private void onKingDeath(Team deadKingTeam) {
        Team winningTeam = (deadKingTeam == Team.WHITE) ? Team.BLACK : Team.WHITE;
        System.out.println("════════════════════════════════════════");
        System.out.println("  🏆 " + winningTeam + " TEAM WINS! 🏆");
        System.out.println("  The enemy king has been slain!");
        System.out.println("════════════════════════════════════════");
        
        // Could trigger morale effects, end-game state, etc.
    }
    
    /**
     * Gets the enemy king for a given team.
     * Lazily scans for kings if not yet found.
     */
    public King getEnemyKing(Team team) {
        // Lazy initialization - scan for kings if we haven't found them yet
        if (whiteKing == null && blackKing == null && entities != null && !entities.isEmpty()) {
            findKings();
        }
        
        if (team == Team.WHITE) {
            return blackKing;
        } else if (team == Team.BLACK) {
            return whiteKing;
        }
        return null;
    }
    
    /**
     * Gets the enemy king's position for pathfinding.
     */
    public Vector3 getEnemyKingPosition(Team team) {
        // Lazy initialization
        if (whiteKing == null && blackKing == null && entities != null && !entities.isEmpty()) {
            findKings();
        }
        
        if (team == Team.WHITE) {
            return blackKingPosition;
        } else if (team == Team.BLACK) {
            return whiteKingPosition;
        }
        return null;
    }
    
    /**
     * Gets a team's own king.
     */
    public King getAlliedKing(Team team) {
        // Lazy initialization
        if (whiteKing == null && blackKing == null && entities != null && !entities.isEmpty()) {
            findKings();
        }
        
        if (team == Team.WHITE) {
            return whiteKing;
        } else if (team == Team.BLACK) {
            return blackKing;
        }
        return null;
    }
    
    /**
     * Checks if a team has won (enemy king is dead).
     */
    public boolean hasTeamWon(Team team) {
        if (team == Team.WHITE) {
            return blackKingDead;
        } else if (team == Team.BLACK) {
            return whiteKingDead;
        }
        return false;
    }
    
    /**
     * Checks if the game is over (any king is dead).
     */
    public boolean isGameOver() {
        return whiteKingDead || blackKingDead;
    }
    
    /**
     * Gets the winning team, or null if game is not over.
     */
    public Team getWinningTeam() {
        if (whiteKingDead) return Team.BLACK;
        if (blackKingDead) return Team.WHITE;
        return null;
    }
    
    /**
     * Resets the objective system for a new game.
     */
    public void reset() {
        whiteKing = null;
        blackKing = null;
        whiteKingDead = false;
        blackKingDead = false;
        entities = null;
    }
}
