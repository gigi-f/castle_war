package com.castlewar.ai.behavior.leaf.tasks;

import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Unit;

/**
 * Task that attacks the current target enemy.
 * <p>
 * Reads target from blackboard and calls unit.attack() if in range.
 * Returns SUCCESS if attack was performed, FAILURE if not possible.
 * 
 * <h2>Blackboard Keys</h2>
 * <ul>
 *   <li>Input: {@link BlackboardKey#COMBAT_CURRENT_TARGET} - Target to attack</li>
 *   <li>Input: {@link BlackboardKey#COMBAT_IN_ATTACK_RANGE} - Whether in range</li>
 * </ul>
 */
public class AttackTask extends TaskNode {
    
    /** Cached unit reference */
    private Unit unit;
    
    /**
     * Creates an attack task.
     */
    public AttackTask() {
        super("attack");
    }
    
    /**
     * Creates an attack task with custom name.
     * 
     * @param name Debug name
     */
    public AttackTask(String name) {
        super(name);
    }
    
    @Override
    protected void onEnter(Blackboard blackboard) {
        unit = blackboard.get(BlackboardKey.SELF_UNIT, Unit.class);
    }
    
    @Override
    protected NodeState execute(Blackboard blackboard) {
        if (unit == null || unit.isDead() || unit.isStunned()) {
            return NodeState.FAILURE;
        }
        
        // Get target
        Object targetObj = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET);
        if (!(targetObj instanceof Unit)) {
            return NodeState.FAILURE;
        }
        
        Unit target = (Unit) targetObj;
        if (target.isDead()) {
            blackboard.remove(BlackboardKey.COMBAT_CURRENT_TARGET);
            return NodeState.FAILURE;
        }
        
        // Check range
        if (!blackboard.getBool(BlackboardKey.COMBAT_IN_ATTACK_RANGE)) {
            return NodeState.FAILURE;
        }
        
        // Stop movement while attacking
        unit.setTargetPosition(null);
        unit.getVelocity().x = 0;
        unit.getVelocity().y = 0;
        
        // Face target
        com.badlogic.gdx.math.Vector3 toTarget = new com.badlogic.gdx.math.Vector3(target.getPosition())
            .sub(unit.getPosition());
        toTarget.z = 0;
        if (toTarget.len2() > 0.01f) {
            unit.getFacing().set(toTarget).nor();
        }
        
        // Perform attack
        unit.attack(target);
        
        return NodeState.SUCCESS;
    }
}
