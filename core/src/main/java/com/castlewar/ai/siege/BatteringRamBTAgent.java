package com.castlewar.ai.siege;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.TransitionableAgent;
import com.castlewar.ai.behavior.BehaviorTree;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.BatteringRam;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;

import java.util.List;

/**
 * Behavior Tree agent for Battering Ram siege engine.
 * 
 * Behavior priorities:
 * 1. Retreat if heavily damaged or enemies swarming
 * 2. Strike gate/wall if in position and wound up
 * 3. Windup if in position
 * 4. Advance toward gate
 * 5. Idle
 * 
 * Battering rams are single-purpose: destroy gates.
 */
public class BatteringRamBTAgent extends TransitionableAgent<BatteringRam, BatteringRamState> {
    
    /** Distance at which ram is in striking position */
    private static final float STRIKE_POSITION_RANGE = 3f;
    
    /** Health threshold for retreat */
    private static final float RETREAT_HEALTH_PERCENT = 0.3f;
    
    /** Number of nearby enemies that triggers retreat */
    private static final int DANGER_ENEMY_COUNT = 4;
    
    /** Detection range for enemies */
    private static final float DANGER_RADIUS = 8f;
    
    /** Target gate position (enemy castle entrance) */
    private Vector3 gateTarget = new Vector3();
    
    /** Whether gate target has been set */
    private boolean gateTargetSet = false;
    
    /**
     * Creates a new Battering Ram BT agent.
     * 
     * @param owner   The BatteringRam this agent controls
     * @param context Access to world state
     */
    public BatteringRamBTAgent(BatteringRam owner, AiContext context) {
        super(owner, context, BatteringRamState.class, BatteringRamState.IDLE);
        
        // Limited vision - focused on gate
        this.visionRange = 20f;
        this.fovDegrees = 120f;
        this.fleeHealthThreshold = RETREAT_HEALTH_PERCENT;
        
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("battering-ram-bt")
            .selector("root")
                // Priority 1: Retreat if critically damaged or swarmed
                .sequence("retreat-behavior")
                    .node(createInDanger())
                    .task(createSetStateTask(BatteringRamState.RETREAT))
                    .task(createRetreatTask())
                .end()
                
                // Priority 2: Strike if wound up and in position
                .sequence("strike-behavior")
                    .node(createInStrikePosition())
                    .node(createIsWindupComplete())
                    .task(createSetStateTask(BatteringRamState.STRIKE))
                    .task(createStrikeTask())
                .end()
                
                // Priority 3: Windup if in position
                .sequence("windup-behavior")
                    .node(createInStrikePosition())
                    .node(createCanStrike())
                    .task(createSetStateTask(BatteringRamState.WINDUP))
                    .task(createWindupTask())
                .end()
                
                // Priority 4: Advance toward gate
                .sequence("advance-behavior")
                    .node(createHasGateTarget())
                    .task(createSetStateTask(BatteringRamState.ADVANCE))
                    .task(createAdvanceTask())
                .end()
                
                // Priority 5: Find gate target
                .sequence("find-gate-behavior")
                    .task(createFindGateTask())
                .end()
                
                // Priority 6: Idle
                .sequence("idle-behavior")
                    .task(createSetStateTask(BatteringRamState.IDLE))
                    .task(createIdleTask())
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Store delta time
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Count nearby enemies
        int nearbyEnemies = 0;
        for (Entity e : context.getEntities()) {
            if (e instanceof Unit && ((Unit) e).getTeam() != owner.getTeam()) {
                Unit unit = (Unit) e;
                if (!unit.isDead()) {
                    float dist = owner.getPosition().dst(e.getPosition());
                    if (dist < DANGER_RADIUS) {
                        nearbyEnemies++;
                    }
                }
            }
        }
        blackboard.setInt(BlackboardKey.CUSTOM_INT_1, nearbyEnemies);
        
        // Store gate target if set
        if (gateTargetSet) {
            blackboard.setVector3(BlackboardKey.MOVEMENT_TARGET_POSITION, gateTarget);
        }
    }
    
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        // Battering rams don't need complex perception - just focus on gate
    }
    
    @Override
    protected float getAttackRange() {
        return BatteringRam.STRIKE_RANGE;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITION FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private ConditionNode createInDanger() {
        return new ConditionNode("inDanger", bb -> {
            // Check health
            float healthPercent = owner.getHp() / owner.getMaxHp();
            if (healthPercent < RETREAT_HEALTH_PERCENT) return true;
            
            // Check enemy count
            int enemies = bb.getInt(BlackboardKey.CUSTOM_INT_1);
            return enemies >= DANGER_ENEMY_COUNT;
        });
    }
    
    private ConditionNode createInStrikePosition() {
        return new ConditionNode("inStrikePosition", bb -> {
            if (!gateTargetSet) return false;
            
            float dist = owner.getPosition().dst(gateTarget);
            return dist <= STRIKE_POSITION_RANGE;
        });
    }
    
    private ConditionNode createIsWindupComplete() {
        return new ConditionNode("isWindupComplete", bb -> {
            return owner.isWindupComplete();
        });
    }
    
    private ConditionNode createCanStrike() {
        return new ConditionNode("canStrike", bb -> {
            return owner.canStrike();
        });
    }
    
    private ConditionNode createHasGateTarget() {
        return new ConditionNode("hasGateTarget", bb -> {
            return gateTargetSet;
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(BatteringRamState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createRetreatTask() {
        return new TaskNode("retreat", bb -> {
            // Move away from gate (back toward friendly territory)
            Vector3 pos = owner.getPosition();
            float delta = bb.getFloat(BlackboardKey.CUSTOM_1);
            float speed = BatteringRam.MOVE_SPEED * 0.8f; // Slower when retreating
            
            // Direction toward own side
            float retreatDir = owner.getTeam() == Team.WHITE ? -1f : 1f;
            pos.x += retreatDir * speed * delta;
            
            // Keep retreating until safer
            float healthPercent = owner.getHp() / owner.getMaxHp();
            int enemies = bb.getInt(BlackboardKey.CUSTOM_INT_1);
            
            if (healthPercent > 0.5f && enemies < 2) {
                return NodeState.SUCCESS;
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createStrikeTask() {
        return new TaskNode("strike", bb -> {
            if (owner.strike()) {
                // Successful strike - damage gate
                // In a full implementation, this would damage the gate block
                owner.decrementGateHits();
                
                // Reset to idle to begin next windup cycle
                owner.setState(BatteringRamState.IDLE);
                return NodeState.SUCCESS;
            }
            return NodeState.FAILURE;
        });
    }
    
    private TaskNode createWindupTask() {
        return new TaskNode("windup", bb -> {
            if (owner.getState() != BatteringRamState.WINDUP) {
                owner.startWindup();
            }
            
            if (owner.isWindupComplete()) {
                return NodeState.SUCCESS;
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createAdvanceTask() {
        return new TaskNode("advance", bb -> {
            Vector3 pos = owner.getPosition();
            float delta = bb.getFloat(BlackboardKey.CUSTOM_1);
            float speed = BatteringRam.MOVE_SPEED;
            
            float dx = gateTarget.x - pos.x;
            float dy = gateTarget.y - pos.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist <= STRIKE_POSITION_RANGE) {
                owner.setState(BatteringRamState.POSITION);
                return NodeState.SUCCESS;
            }
            
            // Move toward gate
            pos.x += (dx / dist) * speed * delta;
            pos.y += (dy / dist) * speed * delta;
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createFindGateTask() {
        return new TaskNode("findGate", bb -> {
            // Find enemy castle gate position
            // For now, estimate based on enemy positions and battlefield center
            
            Vector3 pos = owner.getPosition();
            Team enemyTeam = owner.getTeam() == Team.WHITE ? Team.BLACK : Team.WHITE;
            
            // Find approximate enemy position (average of enemy units)
            float enemyX = 0, enemyY = 0;
            int count = 0;
            
            for (Entity e : context.getEntities()) {
                if (e instanceof Unit && ((Unit) e).getTeam() == enemyTeam) {
                    Unit unit = (Unit) e;
                    if (!unit.isDead()) {
                        enemyX += e.getPosition().x;
                        enemyY += e.getPosition().y;
                        count++;
                    }
                }
            }
            
            if (count > 0) {
                enemyX /= count;
                enemyY /= count;
                
                // Gate is on the friendly-facing side of enemy castle
                // Adjust Y based on our position relative to enemy
                float gateOffsetY = pos.y < enemyY ? -10 : 10;
                
                gateTarget.set(enemyX, enemyY + gateOffsetY, pos.z);
                gateTargetSet = true;
                
                return NodeState.SUCCESS;
            }
            
            return NodeState.FAILURE;
        });
    }
    
    private TaskNode createIdleTask() {
        return new TaskNode("idle", bb -> {
            // Just wait
            return NodeState.SUCCESS;
        });
    }
}
