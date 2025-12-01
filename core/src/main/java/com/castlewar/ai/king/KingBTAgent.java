package com.castlewar.ai.king;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.TransitionableAgent;
import com.castlewar.ai.behavior.BehaviorTree;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.behavior.leaf.conditions.*;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for King units.
 * <p>
 * Kings have defensive/survival-focused behaviors:
 * <ol>
 *   <li><b>Flee to Guards</b> - When threatened, run toward nearest friendly guard</li>
 *   <li><b>Alert</b> - When enemies detected, prepare for defense</li>
 *   <li><b>Patrol</b> - Default behavior, stay in courtyard area</li>
 * </ol>
 */
public class KingBTAgent extends TransitionableAgent<King, KingState> {
    
    /** Distance at which king considers enemies a threat */
    private static final float THREAT_RANGE = 15f;
    
    /** Distance to seek guards */
    private static final float GUARD_SEEK_RANGE = 30f;
    
    /** Distance considered "safe" near a guard */
    private static final float SAFE_DISTANCE_TO_GUARD = 4f;
    
    /**
     * Creates a new King BT agent.
     * 
     * @param owner   The King unit this agent controls
     * @param context Access to world state
     */
    public KingBTAgent(King owner, AiContext context) {
        super(owner, context, KingState.class, KingState.IDLE);
        
        // Configure king-specific settings
        this.visionRange = 20f;
        this.fovDegrees = 180f; // Kings are observant
        this.fleeHealthThreshold = 0.5f; // Kings flee earlier
        
        // Initialize behavior tree
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("king-bt")
            .selector("root")
                // Priority 1: Flee to nearest guard when threatened
                .sequence("flee-to-guard")
                    .condition("isUnderThreat", this::isUnderThreat)
                    .condition("hasNearbyGuard", this::hasNearbyGuard)
                    .task(createSetStateTask(KingState.ALERT))
                    .task(createFleeToGuardTask())
                .end()
                
                // Priority 2: Flee away from threat if no guards
                .sequence("flee-desperate")
                    .condition("isUnderThreat", this::isUnderThreat)
                    .task(createSetStateTask(KingState.ALERT))
                    .task(createFleeFromThreatTask())
                .end()
                
                // Priority 3: Alert when enemies detected (but not close)
                .sequence("alert-behavior")
                    .node(AreEnemiesNearby.any())
                    .task(createSetStateTask(KingState.ALERT))
                    .task(createAlertStanceTask())
                .end()
                
                // Priority 4: Idle patrol in courtyard
                .sequence("idle-behavior")
                    .task(createSetStateTask(KingState.PATROL))
                    .task(createCourtyardIdleTask())
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Store delta time
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Update guard information
        updateGuardPerception();
    }
    
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        
        // Build threat list
        List<Entity> threats = new ArrayList<>();
        Entity nearestThreat = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist <= THREAT_RANGE) {
                threats.add(unit);
                
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestThreat = unit;
                }
            }
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        
        if (nearestThreat != null) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, nearestThreat);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, nearestDist); // THREAT_DISTANCE
        }
    }
    
    /**
     * Finds and stores information about nearby friendly guards.
     */
    private void updateGuardPerception() {
        List<Entity> guards = new ArrayList<>();
        Guard closestGuard = null;
        float closestDist = Float.MAX_VALUE;
        
        for (Entity e : context.getEntities()) {
            if (!(e instanceof Guard)) continue;
            Guard guard = (Guard) e;
            
            if (guard.getTeam() != owner.getTeam()) continue;
            if (guard.isDead()) continue;
            
            float dist = owner.getPosition().dst(guard.getPosition());
            if (dist <= GUARD_SEEK_RANGE) {
                guards.add(guard);
                
                if (dist < closestDist) {
                    closestDist = dist;
                    closestGuard = guard;
                }
            }
        }
        
        blackboard.setList(BlackboardKey.SOCIAL_NEARBY_ALLIES, guards);
        
        if (closestGuard != null) {
            blackboard.set(BlackboardKey.SOCIAL_SQUAD_LEADER, closestGuard);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_2, closestDist); // GUARD_DISTANCE
        } else {
            blackboard.clear(BlackboardKey.SOCIAL_SQUAD_LEADER);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITION PREDICATES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private boolean isUnderThreat(Blackboard bb) {
        Entity threat = bb.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
        if (threat == null) return false;
        if (threat instanceof Unit && ((Unit) threat).isDead()) return false;
        
        float dist = bb.getFloat(BlackboardKey.CUSTOM_FLOAT_1);
        // Threat if enemy within 10 units
        return dist <= 10f;
    }
    
    private boolean hasNearbyGuard(Blackboard bb) {
        Entity guard = bb.get(BlackboardKey.SOCIAL_SQUAD_LEADER, Entity.class);
        if (guard == null) return false;
        return !(guard instanceof Unit) || !((Unit) guard).isDead();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(KingState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createFleeToGuardTask() {
        final float fleeSpeed = 6f;
        
        return new TaskNode("fleeToGuard", bb -> {
            Entity guard = bb.get(BlackboardKey.SOCIAL_SQUAD_LEADER, Entity.class);
            if (guard == null || (guard instanceof Unit && ((Unit) guard).isDead())) {
                return NodeState.FAILURE;
            }
            
            float distToGuard = owner.getPosition().dst(guard.getPosition());
            
            // Already safe near guard
            if (distToGuard <= SAFE_DISTANCE_TO_GUARD) {
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Move toward guard
            Vector3 toGuard = new Vector3(guard.getPosition()).sub(owner.getPosition());
            toGuard.z = 0;
            toGuard.nor();
            
            owner.getVelocity().x = toGuard.x * fleeSpeed;
            owner.getVelocity().y = toGuard.y * fleeSpeed;
            owner.getFacing().set(toGuard);
            owner.setTargetPosition(guard.getPosition());
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createFleeFromThreatTask() {
        final float fleeSpeed = 5f;
        
        return new TaskNode("fleeFromThreat", bb -> {
            Entity threat = bb.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
            if (threat == null || (threat instanceof Unit && ((Unit) threat).isDead())) {
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Flee directly away from threat
            Vector3 awayFromThreat = new Vector3(owner.getPosition()).sub(threat.getPosition());
            awayFromThreat.z = 0;
            
            if (!awayFromThreat.isZero(0.01f)) {
                awayFromThreat.nor();
                owner.getVelocity().x = awayFromThreat.x * fleeSpeed;
                owner.getVelocity().y = awayFromThreat.y * fleeSpeed;
                owner.getFacing().set(awayFromThreat);
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createAlertStanceTask() {
        return new TaskNode("alertStance", bb -> {
            Entity threat = bb.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
            
            // Stop movement
            owner.getVelocity().x = 0;
            owner.getVelocity().y = 0;
            owner.setTargetPosition(null);
            
            // Face the threat if known
            if (threat != null && (!(threat instanceof Unit) || !((Unit) threat).isDead())) {
                Vector3 toThreat = new Vector3(threat.getPosition()).sub(owner.getPosition());
                toThreat.z = 0;
                if (!toThreat.isZero(0.01f)) {
                    toThreat.nor();
                    owner.getFacing().set(toThreat);
                }
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createCourtyardIdleTask() {
        return new TaskNode("courtyardIdle", bb -> {
            float delta = bb.getFloat(BlackboardKey.CUSTOM_1);
            
            // Stop movement
            owner.getVelocity().x = 0;
            owner.getVelocity().y = 0;
            owner.setTargetPosition(null);
            
            // Occasionally look around (random facing every few seconds)
            if (MathUtils.random() < delta * 0.3f) {
                float angle = MathUtils.random(-30f, 30f);
                owner.getFacing().rotate(Vector3.Z, angle);
            }
            
            return NodeState.RUNNING;
        });
    }
}
