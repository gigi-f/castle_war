package com.castlewar.ai.archer;

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
import com.castlewar.entity.Archer;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for Archer units.
 * <p>
 * Archers prioritize positioning and kiting:
 * <ol>
 *   <li><b>Kite</b> - When melee threat nearby, retreat while attacking</li>
 *   <li><b>Take Cover</b> - When under fire and cover available</li>
 *   <li><b>Fire</b> - When target in range and can attack</li>
 *   <li><b>Reposition</b> - Move to better vantage point</li>
 *   <li><b>Patrol</b> - Default scouting behavior</li>
 * </ol>
 * 
 * <h2>Archer Mechanics</h2>
 * <ul>
 *   <li>Long range (12 units) but slow attack speed</li>
 *   <li>Elevation bonus (+15% damage from high ground)</li>
 *   <li>Cover bonus (+25% defense behind obstacles)</li>
 *   <li>Melee vulnerability (+50% damage from close attacks)</li>
 *   <li>Limited arrows (30 per archer)</li>
 * </ul>
 */
public class ArcherBTAgent extends TransitionableAgent<Archer, ArcherState> {
    
    /** Range to detect enemies for targeting */
    private static final float TARGET_RANGE = 14f;
    
    /** Range at which archer starts kiting */
    private static final float KITE_TRIGGER_RANGE = 6f;
    
    /** Ideal attack distance (not too close, not too far) */
    private static final float IDEAL_ATTACK_DISTANCE = 10f;
    
    /** Minimum arrows before seeking resupply */
    private static final int LOW_ARROW_THRESHOLD = 5;
    
    /** Patrol waypoints */
    private final List<Vector3> patrolWaypoints = new ArrayList<>();
    
    /** Whether waypoints are initialized */
    private boolean waypointsInitialized = false;
    
    /**
     * Creates a new Archer BT agent.
     * 
     * @param owner   The Archer unit this agent controls
     * @param context Access to world state
     */
    public ArcherBTAgent(Archer owner, AiContext context) {
        super(owner, context, ArcherState.class, ArcherState.IDLE);
        
        // Configure archer-specific settings
        this.visionRange = 18f;  // Archers have better sight
        this.fovDegrees = 160f;  // Wide field of view
        this.fleeHealthThreshold = 0.4f;  // Retreat earlier (fragile)
        
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("archer-bt")
            .selector("root")
                // Priority 1: Kite when melee threat is too close
                .sequence("kite-behavior")
                    .node(createHasMeleeThreat())
                    .task(createSetStateTask(ArcherState.KITING))
                    .task(createKiteTask())
                .end()
                
                // Priority 2: Take cover when low health
                .sequence("cover-behavior")
                    .node(IsLowHealth.standard())
                    .task(createSetStateTask(ArcherState.TAKING_COVER))
                    .task(createTakeCoverTask())
                .end()
                
                // Priority 3: Fire at target in range
                .sequence("fire-behavior")
                    .node(HasTarget.create())
                    .node(createIsTargetInRangedRange())
                    .node(createHasArrows())
                    .task(createSetStateTask(ArcherState.FIRING))
                    .task(createFireTask())
                .end()
                
                // Priority 4: Acquire target if enemies visible
                .sequence("acquire-target-behavior")
                    .node(createHasEnemiesInVision())
                    .selector("acquire-or-reposition")
                        .sequence("acquire")
                            .node(createNoCurrentTarget())
                            .task(createAcquireTargetTask())
                        .end()
                        .sequence("reposition-for-shot")
                            .node(createTargetTooClose())
                            .task(createSetStateTask(ArcherState.REPOSITIONING))
                            .task(createRepositionTask())
                        .end()
                    .end()
                .end()
                
                // Priority 5: Retreat when critically low health
                .sequence("retreat-behavior")
                    .node(createIsCriticalHealth())
                    .task(createSetStateTask(ArcherState.RETREATING))
                    .task(createRetreatTask())
                .end()
                
                // Priority 6: Default patrol
                .sequence("patrol-behavior")
                    .task(createSetStateTask(ArcherState.IDLE))
                    .task(new PatrolTask("archer-patrol", 3f, 1.5f))
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
        
        // Create a patrol pattern (elevated positions preferred for archers)
        patrolWaypoints.add(new Vector3(home.x + radius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x + radius * 0.7f, home.y + radius * 0.7f, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y + radius, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius * 0.7f, home.y + radius * 0.7f, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x - radius * 0.7f, home.y - radius * 0.7f, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y - radius, home.z));
        patrolWaypoints.add(new Vector3(home.x + radius * 0.7f, home.y - radius * 0.7f, home.z));
        
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
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        
        if (closestThreat != null) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, closestThreat);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, closestThreatDist);
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
    
    /**
     * Checks if there's a melee threat within kite trigger range.
     */
    private ConditionNode createHasMeleeThreat() {
        return new ConditionNode("hasMeleeThreat", bb -> owner.hasMeleeThreat());
    }
    
    /**
     * Checks if target is within ranged attack range.
     */
    private ConditionNode createIsTargetInRangedRange() {
        return new ConditionNode("targetInRangedRange", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                return false;
            }
            
            float distance = owner.getPosition().dst(target.getPosition());
            return distance <= owner.getAttackRange();
        });
    }
    
    /**
     * Checks if archer has arrows to fire.
     */
    private ConditionNode createHasArrows() {
        return new ConditionNode("hasArrows", bb -> owner.getArrowCount() > 0);
    }
    
    /**
     * Checks if there are enemies in vision range.
     */
    private ConditionNode createHasEnemiesInVision() {
        return new ConditionNode("hasEnemiesInVision", bb -> {
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            return threats != null && !threats.isEmpty();
        });
    }
    
    /**
     * Checks if there's no current target.
     */
    private ConditionNode createNoCurrentTarget() {
        return new ConditionNode("noCurrentTarget", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            return target == null || (target instanceof Unit && ((Unit) target).isDead());
        });
    }
    
    /**
     * Checks if target is too close (need to reposition).
     */
    private ConditionNode createTargetTooClose() {
        return new ConditionNode("targetTooClose", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                return false;
            }
            
            float distance = owner.getPosition().dst(target.getPosition());
            return distance < IDEAL_ATTACK_DISTANCE * 0.5f;
        });
    }
    
    /**
     * Checks if health is critically low.
     */
    private ConditionNode createIsCriticalHealth() {
        return new ConditionNode("isCriticalHealth", bb -> {
            float hpPercent = bb.getFloat(BlackboardKey.SELF_HP_PERCENT);
            return hpPercent < 0.2f;
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a task that sets the archer's FSM state.
     */
    private TaskNode createSetStateTask(ArcherState newState) {
        return new TaskNode("setState" + newState.name()) {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                owner.setCurrentState(newState);
                return NodeState.SUCCESS;
            }
        };
    }
    
    /**
     * Creates task for kiting away from melee threats.
     */
    private TaskNode createKiteTask() {
        return new TaskNode("kite") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                // Get direction away from threats
                Vector3 kiteDir = owner.getKiteDirection();
                if (kiteDir.len2() < 0.001f) {
                    owner.setKiting(false);
                    return NodeState.SUCCESS;
                }
                
                owner.setKiting(true);
                
                // Move away while maintaining attack if possible
                owner.getVelocity().set(kiteDir);
                
                // Try to fire while kiting
                Entity target = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
                if (target instanceof Unit) {
                    Unit targetUnit = (Unit) target;
                    if (!targetUnit.isDead() && owner.canAttack()) {
                        float distance = owner.getPosition().dst(targetUnit.getPosition());
                        if (distance <= owner.getAttackRange()) {
                            owner.fireArrow(targetUnit);
                        }
                    }
                }
                
                return NodeState.RUNNING;
            }
        };
    }
    
    /**
     * Creates task for moving to cover.
     */
    private TaskNode createTakeCoverTask() {
        return new TaskNode("takeCover") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                // Try to find a cover position
                Vector3 coverPos = owner.findCoverPosition(context.getGrid());
                
                if (coverPos == null) {
                    // No cover available, just try to move away from threats
                    Vector3 kiteDir = owner.getKiteDirection();
                    if (kiteDir.len2() > 0.001f) {
                        owner.getVelocity().set(kiteDir);
                        return NodeState.RUNNING;
                    }
                    return NodeState.FAILURE;
                }
                
                // Move toward cover
                Vector3 toCover = new Vector3(coverPos).sub(owner.getPosition());
                float distance = toCover.len();
                
                if (distance < 1.0f) {
                    owner.getVelocity().setZero();
                    return NodeState.SUCCESS;
                }
                
                toCover.nor();
                owner.getVelocity().set(toCover);
                
                return NodeState.RUNNING;
            }
        };
    }
    
    /**
     * Creates task for firing at target.
     */
    private TaskNode createFireTask() {
        return new TaskNode("fire") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Entity target = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
                if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                    return NodeState.FAILURE;
                }
                
                Unit targetUnit = (Unit) target;
                
                // Face target
                Vector3 toTarget = new Vector3(targetUnit.getPosition()).sub(owner.getPosition()).nor();
                owner.getFacing().set(toTarget);
                
                // Stop moving while aiming
                owner.getVelocity().setZero();
                owner.setKiting(false);
                
                // Fire if ready
                if (owner.canAttack()) {
                    if (owner.fireArrow(targetUnit)) {
                        return NodeState.SUCCESS;
                    }
                }
                
                // Wait for cooldown
                owner.setCurrentState(ArcherState.AIMING);
                return NodeState.RUNNING;
            }
        };
    }
    
    /**
     * Creates task for acquiring a new target.
     */
    private TaskNode createAcquireTargetTask() {
        return new TaskNode("acquireTarget") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                List<Entity> threats = blackboard.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
                if (threats == null || threats.isEmpty()) {
                    return NodeState.FAILURE;
                }
                
                Unit bestTarget = null;
                float bestScore = Float.MAX_VALUE;
                
                for (Entity e : threats) {
                    if (!(e instanceof Unit)) continue;
                    Unit unit = (Unit) e;
                    if (unit.isDead()) continue;
                    
                    float distance = owner.getPosition().dst(unit.getPosition());
                    
                    // Prefer closer targets and wounded targets
                    float score = distance + (unit.getHp() / unit.getMaxHp()) * 5f;
                    if (score < bestScore) {
                        bestScore = score;
                        bestTarget = unit;
                    }
                }
                
                if (bestTarget != null) {
                    blackboard.set(BlackboardKey.COMBAT_CURRENT_TARGET, bestTarget);
                    return NodeState.SUCCESS;
                }
                
                return NodeState.FAILURE;
            }
        };
    }
    
    /**
     * Creates task for repositioning to ideal range.
     */
    private TaskNode createRepositionTask() {
        return new TaskNode("reposition") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                Entity target = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
                if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                    return NodeState.FAILURE;
                }
                
                Vector3 awayFromTarget = new Vector3(owner.getPosition())
                    .sub(target.getPosition()).nor();
                
                // Move away to ideal distance
                float currentDist = owner.getPosition().dst(target.getPosition());
                
                if (currentDist >= IDEAL_ATTACK_DISTANCE * 0.8f) {
                    owner.getVelocity().setZero();
                    return NodeState.SUCCESS;
                }
                
                owner.getVelocity().set(awayFromTarget);
                return NodeState.RUNNING;
            }
        };
    }
    
    /**
     * Creates task for retreating when critically wounded.
     */
    private TaskNode createRetreatTask() {
        return new TaskNode("retreat") {
            @Override
            protected NodeState execute(Blackboard blackboard) {
                List<Entity> entities = context.getEntities();
                Vector3 retreatDir = new Vector3();
                int allyCount = 0;
                
                // Find nearest ally to retreat toward
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
                    for (Entity e : entities) {
                        if (e instanceof Unit && e != owner) {
                            Unit unit = (Unit) e;
                            if (unit.getTeam() != owner.getTeam() && !unit.isDead()) {
                                float distance = owner.getPosition().dst(unit.getPosition());
                                if (distance < 15f) {
                                    Vector3 away = new Vector3(owner.getPosition()).sub(unit.getPosition()).nor();
                                    retreatDir.add(away);
                                }
                            }
                        }
                    }
                    if (retreatDir.len2() > 0) {
                        retreatDir.nor();
                    }
                }
                
                if (retreatDir.len2() > 0.001f) {
                    owner.getVelocity().set(retreatDir);
                    return NodeState.RUNNING;
                }
                
                return NodeState.SUCCESS;
            }
        };
    }
    
    @Override
    public AiContext getContext() {
        return context;
    }
}
