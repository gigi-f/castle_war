package com.castlewar.ai.cavalry;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.TransitionableAgent;
import com.castlewar.ai.behavior.BehaviorTree;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.behavior.leaf.conditions.*;
import com.castlewar.ai.behavior.leaf.tasks.*;
import com.castlewar.ai.blackboard.Blackboard;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Cavalry;
import com.castlewar.entity.Entity;
import com.castlewar.entity.King;
import com.castlewar.entity.Unit;
import com.castlewar.simulation.TeamObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for Cavalry units.
 * <p>
 * Cavalry prioritize charge attacks and mobility:
 * <ol>
 *   <li><b>Charge</b> - When enemy at optimal distance, charge!</li>
 *   <li><b>Wheel</b> - After charge, wheel around for next pass</li>
 *   <li><b>Engage</b> - Melee combat when charge not possible</li>
 *   <li><b>Retreat</b> - When horse is badly wounded</li>
 *   <li><b>Patrol</b> - Default scouting behavior</li>
 * </ol>
 * 
 * <h2>Cavalry Mechanics</h2>
 * <ul>
 *   <li>High speed (14 units/sec at full gallop)</li>
 *   <li>Charge damage scales with speed</li>
 *   <li>Require 10 units distance for charge</li>
 *   <li>3 second cooldown between charges</li>
 *   <li>If horse dies, fight as dismounted infantry</li>
 * </ul>
 */
public class CavalryBTAgent extends TransitionableAgent<Cavalry, CavalryState> {
    
    /** Range to detect enemies for targeting */
    private static final float TARGET_RANGE = 20f;
    
    /** Ideal distance to start a charge */
    private static final float IDEAL_CHARGE_DISTANCE = 15f;
    
    /** Distance at which to start acquiring charge targets */
    private static final float CHARGE_DETECTION_RANGE = 25f;
    
    /** Patrol waypoints */
    private final List<Vector3> patrolWaypoints = new ArrayList<>();
    
    /** Whether waypoints are initialized */
    private boolean waypointsInitialized = false;
    
    /**
     * Creates a new Cavalry BT agent.
     * 
     * @param owner   The Cavalry unit this agent controls
     * @param context Access to world state
     */
    public CavalryBTAgent(Cavalry owner, AiContext context) {
        super(owner, context, CavalryState.class, CavalryState.IDLE);
        
        // Configure cavalry-specific settings
        this.visionRange = 22f;  // Cavalry have good sight from horseback
        this.fovDegrees = 180f;  // Wide view
        this.fleeHealthThreshold = 0.3f;
        
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("cavalry-bt")
            .selector("root")
                // Priority 1: Continue charging if already charging
                .sequence("continue-charge")
                    .node(createIsCharging())
                    .task(createContinueChargeTask())
                .end()
                
                // Priority 2: Wheel around after charge
                .sequence("wheel-behavior")
                    .node(createIsWheeling())
                    .task(createSetStateTask(CavalryState.WHEELING))
                    .task(createWheelTask())
                .end()
                
                // Priority 3: Initiate charge when opportunity arises
                .sequence("charge-behavior")
                    .node(createIsMounted())
                    .node(createCanCharge())
                    .node(createHasChargeTarget())
                    .task(createSetStateTask(CavalryState.CHARGING))
                    .task(createStartChargeTask())
                .end()
                
                // Priority 4: Retreat when horse is wounded
                .sequence("retreat-behavior")
                    .node(createHorseWounded())
                    .task(createSetStateTask(CavalryState.RETREATING))
                    .task(createRetreatTask())
                .end()
                
                // Priority 5: Engage in melee when close to enemy
                .sequence("engage-behavior")
                    .node(HasTarget.create())
                    .node(IsTargetInRange.melee())
                    .task(createSetStateTask(CavalryState.ENGAGING))
                    .task(createEngageTask())
                .end()
                
                // Priority 6: Approach enemies for charge
                .sequence("approach-behavior")
                    .node(createHasEnemiesInVision())
                    .node(createIsMounted())
                    .task(createSetStateTask(CavalryState.CANTERING))
                    .task(createApproachForChargeTask())
                .end()
                
                // Priority 7: ALWAYS ride toward enemy king (collective objective)
                .sequence("regicide-behavior")
                    .node(createIsMounted())
                    .node(ShouldTargetKing.enemyKingExists())
                    .task(TargetEnemyKingTask.create())
                    .task(createSetStateTask(CavalryState.CANTERING))
                    .task(createRideToKingTask())
                .end()
                
                // Priority 8: Default patrol (fallback if no king)
                .sequence("patrol-behavior")
                    .task(createSetPatrolState())
                    .task(new PatrolTask("cavalry-patrol", 2f, 1.5f))
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Initialize waypoints
        if (!waypointsInitialized) {
            initializePatrolWaypoints();
            waypointsInitialized = true;
        }
        
        // Store delta time
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Update mounted status
        blackboard.set(BlackboardKey.VEHICLE_IS_MOUNTED, owner.isMounted());
        blackboard.set(BlackboardKey.VEHICLE_IS_CHARGING, owner.isCharging());
        
        // Update attack range
        blackboard.setFloat(BlackboardKey.COMBAT_ATTACK_RANGE, owner.getAttackRange());
    }
    
    /**
     * Initializes patrol waypoints around spawn position.
     */
    private void initializePatrolWaypoints() {
        patrolWaypoints.clear();
        
        Vector3 home = new Vector3(owner.getPosition());
        float radius = owner.getPatrolRadius();
        
        // Large patrol pattern for cavalry
        patrolWaypoints.add(new Vector3(home.x + radius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x + radius, home.y + radius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y + radius, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius, home.y + radius, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius, home.y - radius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y - radius, home.z));
        patrolWaypoints.add(new Vector3(home.x + radius, home.y - radius, home.z));
        
        blackboard.setList(BlackboardKey.MOVEMENT_PATROL_WAYPOINTS, patrolWaypoints);
        blackboard.setVector3(BlackboardKey.MOVEMENT_HOME_POSITION, home);
    }
    
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        
        // Build threat list with distance info
        List<Entity> threats = new ArrayList<>();
        Entity closestThreat = null;
        float closestThreatDist = Float.MAX_VALUE;
        Entity bestChargeTarget = null;
        float bestChargeDistance = Float.MAX_VALUE;
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist > visionRange) continue;
            if (!isInFieldOfView(unit)) continue;
            
            threats.add(unit);
            
            if (dist < closestThreatDist) {
                closestThreatDist = dist;
                closestThreat = unit;
            }
            
            // Find best charge target (at ideal distance)
            if (dist >= Cavalry.MIN_CHARGE_DISTANCE && dist <= CHARGE_DETECTION_RANGE) {
                float distToIdeal = Math.abs(dist - IDEAL_CHARGE_DISTANCE);
                if (distToIdeal < bestChargeDistance) {
                    bestChargeDistance = distToIdeal;
                    bestChargeTarget = unit;
                }
            }
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        
        if (closestThreat != null) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, closestThreat);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, closestThreatDist);
        }
        
        // Store best charge target
        if (bestChargeTarget != null) {
            blackboard.set(BlackboardKey.VEHICLE_CHARGE_TARGET, bestChargeTarget.getPosition());
            blackboard.set(BlackboardKey.CUSTOM_ENTITY_1, bestChargeTarget);
        } else {
            blackboard.clear(BlackboardKey.VEHICLE_CHARGE_TARGET);
            blackboard.clear(BlackboardKey.CUSTOM_ENTITY_1);
        }
        
        // Set combat target
        Entity currentTarget = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
        if (currentTarget == null || (currentTarget instanceof Unit && ((Unit) currentTarget).isDead())) {
            if (closestThreat != null) {
                blackboard.set(BlackboardKey.COMBAT_CURRENT_TARGET, closestThreat);
            }
        }
    }
    
    @Override
    protected float getAttackRange() {
        return owner.getAttackRange();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITION FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private ConditionNode createIsMounted() {
        return new ConditionNode("isMounted", bb -> owner.isMounted());
    }
    
    private ConditionNode createIsCharging() {
        return new ConditionNode("isCharging", bb -> owner.isCharging());
    }
    
    private ConditionNode createIsWheeling() {
        return new ConditionNode("isWheeling", bb -> owner.isWheeling());
    }
    
    private ConditionNode createCanCharge() {
        return new ConditionNode("canCharge", bb -> owner.canCharge());
    }
    
    private ConditionNode createHasChargeTarget() {
        return new ConditionNode("hasChargeTarget", bb -> {
            Entity target = bb.get(BlackboardKey.CUSTOM_ENTITY_1, Entity.class);
            return target != null && (!(target instanceof Unit) || !((Unit) target).isDead());
        });
    }
    
    private ConditionNode createHorseWounded() {
        return new ConditionNode("horseWounded", bb -> {
            if (!owner.isMounted()) return false;
            float horseHpPercent = owner.getHorseHp() / owner.getHorseMaxHp();
            return horseHpPercent < 0.3f;
        });
    }
    
    private ConditionNode createHasEnemiesInVision() {
        return new ConditionNode("hasEnemiesInVision", bb -> {
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            return threats != null && !threats.isEmpty();
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(CavalryState newState) {
        return new TaskNode("setState" + newState.name()) {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                owner.setCurrentState(newState);
                return NodeState.SUCCESS;
            }
        };
    }
    
    private TaskNode createSetPatrolState() {
        return new TaskNode("setPatrolState") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                if (owner.isMounted()) {
                    owner.setCurrentState(CavalryState.TROTTING);
                } else {
                    owner.setCurrentState(CavalryState.IDLE);
                }
                return NodeState.SUCCESS;
            }
        };
    }
    
    private TaskNode createContinueChargeTask() {
        return new TaskNode("continueCharge") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                // Charge is handled in Cavalry.updateCharge()
                // Just return RUNNING while charging
                if (owner.isCharging()) {
                    return NodeState.RUNNING;
                }
                return NodeState.SUCCESS;
            }
        };
    }
    
    private TaskNode createWheelTask() {
        return new TaskNode("wheel") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                if (!owner.isWheeling()) {
                    return NodeState.SUCCESS;
                }
                
                // Move in a wide arc to turn around
                Vector3 perpendicular = new Vector3(-owner.getFacing().y, owner.getFacing().x, 0).nor();
                owner.getVelocity().set(perpendicular).scl(owner.getTrotSpeed());
                
                return NodeState.RUNNING;
            }
        };
    }
    
    private TaskNode createStartChargeTask() {
        return new TaskNode("startCharge") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Entity target = blackboard.get(BlackboardKey.CUSTOM_ENTITY_1, Entity.class);
                if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                    return NodeState.FAILURE;
                }
                
                if (owner.startCharge((Unit) target)) {
                    return NodeState.SUCCESS;
                }
                return NodeState.FAILURE;
            }
        };
    }
    
    private TaskNode createRetreatTask() {
        return new TaskNode("retreat") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                // Retreat toward allies or away from enemies
                List<Entity> entities = context.getEntities();
                Vector3 retreatDir = new Vector3();
                int allyCount = 0;
                
                if (entities != null) {
                    for (Entity e : entities) {
                        if (e instanceof Unit && e != owner) {
                            Unit unit = (Unit) e;
                            if (unit.getTeam() == owner.getTeam() && !unit.isDead()) {
                                Vector3 toAlly = new Vector3(unit.getPosition()).sub(owner.getPosition()).nor();
                                retreatDir.add(toAlly);
                                allyCount++;
                            }
                        }
                    }
                }
                
                if (allyCount > 0) {
                    retreatDir.scl(1f / allyCount).nor();
                } else {
                    // No allies, retreat away from nearest enemy
                    Entity threat = blackboard.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
                    if (threat != null) {
                        retreatDir.set(owner.getPosition()).sub(threat.getPosition()).nor();
                    }
                }
                
                if (retreatDir.len2() > 0.001f) {
                    owner.getVelocity().set(retreatDir).scl(owner.getCurrentMoveSpeed());
                    return NodeState.RUNNING;
                }
                
                return NodeState.SUCCESS;
            }
        };
    }
    
    private TaskNode createEngageTask() {
        return new TaskNode("engage") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Entity target = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
                if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                    return NodeState.FAILURE;
                }
                
                Unit targetUnit = (Unit) target;
                float distance = owner.getPosition().dst(targetUnit.getPosition());
                
                // Move closer if needed
                if (distance > owner.getAttackRange()) {
                    Vector3 toTarget = new Vector3(targetUnit.getPosition()).sub(owner.getPosition()).nor();
                    owner.getVelocity().set(toTarget).scl(owner.getCurrentMoveSpeed());
                    return NodeState.RUNNING;
                }
                
                // Attack
                owner.getVelocity().setZero();
                if (owner.canAttack()) {
                    owner.attack(targetUnit);
                }
                
                return NodeState.RUNNING;
            }
        };
    }
    
    private TaskNode createApproachForChargeTask() {
        return new TaskNode("approachForCharge") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Entity target = blackboard.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
                if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                    return NodeState.FAILURE;
                }
                
                float distance = owner.getPosition().dst(target.getPosition());
                
                // If at ideal charge distance and can charge, succeed to allow charge behavior
                if (distance >= Cavalry.MIN_CHARGE_DISTANCE && 
                    distance <= IDEAL_CHARGE_DISTANCE * 1.2f &&
                    owner.canCharge()) {
                    // Store as charge target
                    blackboard.set(BlackboardKey.CUSTOM_ENTITY_1, target);
                    return NodeState.SUCCESS;
                }
                
                // Move to get into charge position
                Vector3 toTarget = new Vector3(target.getPosition()).sub(owner.getPosition()).nor();
                
                if (distance < Cavalry.MIN_CHARGE_DISTANCE) {
                    // Too close, back off
                    toTarget.scl(-1);
                }
                
                owner.getVelocity().set(toTarget).scl(owner.getCanterSpeed());
                return NodeState.RUNNING;
            }
        };
    }
    
    @Override
    public AiContext getContext() {
        return context;
    }
    
    /**
     * Creates a task that rides toward the enemy king at canter speed.
     * This implements the collective "kill the enemy king" objective.
     */
    private TaskNode createRideToKingTask() {
        return new TaskNode("rideToKing") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Vector3 kingPos = blackboard.getVector3(BlackboardKey.MOVEMENT_TARGET_POSITION);
                if (kingPos == null) {
                    return NodeState.FAILURE;
                }
                
                float distance = owner.getPosition().dst(kingPos);
                
                // Close enough - will be handled by charge/engage behavior next tick
                if (distance <= IDEAL_CHARGE_DISTANCE && owner.canCharge()) {
                    // Close enough for charge - let charge behavior take over
                    King enemyKing = TeamObjective.getInstance().getEnemyKing(owner.getTeam());
                    if (enemyKing != null && !enemyKing.isDead()) {
                        blackboard.set(BlackboardKey.CUSTOM_ENTITY_1, enemyKing);
                    }
                    return NodeState.SUCCESS;
                }
                
                // Ride toward king
                Vector3 toKing = new Vector3(kingPos).sub(owner.getPosition()).nor();
                owner.getVelocity().set(toKing).scl(owner.getCanterSpeed());
                owner.getFacing().set(toKing.x, toKing.y, 0).nor();
                
                return NodeState.RUNNING;
            }
        };
    }
}
