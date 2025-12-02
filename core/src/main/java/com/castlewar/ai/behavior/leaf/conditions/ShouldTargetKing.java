package com.castlewar.ai.behavior.leaf.conditions;

import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.King;
import com.castlewar.entity.Unit;
import com.castlewar.simulation.TeamObjective;

/**
 * Condition that checks if the enemy king should be prioritized as a target.
 * <p>
 * Returns SUCCESS if:
 * <ul>
 *   <li>An enemy king exists and is alive</li>
 *   <li>The unit is within a certain distance threshold</li>
 *   <li>OR no other enemies are nearby (king becomes default objective)</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * Use this condition in behavior trees to gate king-targeting behavior:
 * <pre>{@code
 * .sequence("regicide-behavior")
 *     .node(ShouldTargetKing.create())
 *     .task(TargetEnemyKingTask.create())
 *     .task(new MoveToTargetTask())
 * .end()
 * }</pre>
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#SELF_UNIT} - The unit checking condition</li>
 *   <li>Input: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Current target (may be null)</li>
 * </ul>
 */
public class ShouldTargetKing extends ConditionNode {
    
    /** Distance within which we should always prioritize the king */
    private static final float PRIORITY_DISTANCE = 30f;
    
    /** Always target king if no other enemies visible */
    private static final boolean TARGET_WHEN_IDLE = true;
    
    /**
     * Creates a ShouldTargetKing condition.
     */
    public ShouldTargetKing() {
        super("should-target-king", ShouldTargetKing::evaluateCondition);
    }
    
    /**
     * Creates a ShouldTargetKing condition with custom name.
     * 
     * @param name Debug name
     */
    public ShouldTargetKing(String name) {
        super(name, ShouldTargetKing::evaluateCondition);
    }
    
    /**
     * Evaluates whether the unit should prioritize targeting the enemy king.
     * 
     * @param blackboard The unit's blackboard
     * @return true if king should be targeted
     */
    private static boolean evaluateCondition(Blackboard blackboard) {
        Unit unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
        if (unit == null || unit.isDead() || unit.getTeam() == null) {
            return false;
        }
        
        TeamObjective objective = TeamObjective.getInstance();
        King enemyKing = objective.getEnemyKing(unit.getTeam());
        
        if (enemyKing == null || enemyKing.isDead()) {
            // No enemy king exists
            return false;
        }
        
        // Always prioritize king if within threshold distance
        float distToKing = unit.getPosition().dst(enemyKing.getPosition());
        if (distToKing <= PRIORITY_DISTANCE) {
            return true;
        }
        
        // Target king when idle (no other target)
        if (TARGET_WHEN_IDLE) {
            Object currentTarget = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Object.class);
            if (currentTarget == null) {
                return true;
            }
            
            // If current target IS the king, keep targeting
            if (currentTarget == enemyKing) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Factory method for creating the condition.
     * 
     * @return New ShouldTargetKing instance
     */
    public static ShouldTargetKing create() {
        return new ShouldTargetKing();
    }
    
    /**
     * Creates a condition that always returns true if enemy king is alive.
     * 
     * @return Condition that checks king existence only
     */
    public static ConditionNode enemyKingExists() {
        return new ConditionNode("enemy-king-exists", bb -> {
            Unit unit = bb.get(BlackboardKey.SELF_UNIT, Unit.class);
            if (unit == null || unit.getTeam() == null) {
                return false;
            }
            
            TeamObjective objective = TeamObjective.getInstance();
            King enemyKing = objective.getEnemyKing(unit.getTeam());
            return enemyKing != null && !enemyKing.isDead();
        });
    }
    
    /**
     * Creates a condition that checks if the enemy king is dead (victory condition).
     * 
     * @return Condition that checks for enemy king death
     */
    public static ConditionNode enemyKingDead() {
        return new ConditionNode("enemy-king-dead", bb -> {
            Unit unit = bb.get(BlackboardKey.SELF_UNIT, Unit.class);
            if (unit == null || unit.getTeam() == null) {
                return false;
            }
            
            TeamObjective objective = TeamObjective.getInstance();
            return objective.hasTeamWon(unit.getTeam());
        });
    }
}
