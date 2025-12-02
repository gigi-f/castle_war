package com.castlewar.ai.infantry;

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
import com.castlewar.entity.Entity;
import com.castlewar.entity.Infantry;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for Infantry units.
 * <p>
 * Infantry have formation-focused behaviors:
 * <ol>
 *   <li><b>Retreat</b> - When low health and isolated, fall back</li>
 *   <li><b>Engage</b> - When enemy in melee range, fight</li>
 *   <li><b>Charge</b> - When enemies detected and in formation, charge together</li>
 *   <li><b>Regroup</b> - When isolated, move toward allies</li>
 *   <li><b>March/Hold</b> - Default formation movement or holding position</li>
 * </ol>
 * 
 * <h2>Formation Mechanics</h2>
 * Infantry gain bonuses when near allies:
 * <ul>
 *   <li>2+ allies within 6 units = "in formation" (+20% damage)</li>
 *   <li>0 allies nearby = "isolated" (-30% damage)</li>
 *   <li>Infantry try to maintain 2.5 unit spacing</li>
 * </ul>
 */
public class InfantryBTAgent extends TransitionableAgent<Infantry, InfantryState> {
    
    /** Range to detect allies for formation */
    private static final float ALLY_RANGE = Infantry.ALLY_DETECTION_RANGE;
    
    /** Range to start charging enemies */
    private static final float CHARGE_RANGE = 15f;
    
    /** Minimum allies for charge behavior */
    private static final int MIN_ALLIES_FOR_CHARGE = 2;
    
    /** Patrol waypoints */
    private final List<Vector3> patrolWaypoints = new ArrayList<>();
    
    /** Whether waypoints are initialized */
    private boolean waypointsInitialized = false;
    
    /**
     * Creates a new Infantry BT agent.
     * 
     * @param owner   The Infantry unit this agent controls
     * @param context Access to world state
     */
    public InfantryBTAgent(Infantry owner, AiContext context) {
        super(owner, context, InfantryState.class, InfantryState.IDLE);
        
        // Configure infantry-specific settings
        this.visionRange = 12f;
        this.fovDegrees = 150f;
        this.fleeHealthThreshold = 0.25f;
        
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("infantry-bt")
            .selector("root")
                // Priority 1: Retreat when low health AND isolated
                .sequence("retreat-behavior")
                    .node(IsLowHealth.standard())
                    .node(createIsIsolated())
                    .task(createSetStateTask(InfantryState.RETREAT))
                    .task(createRetreatTask())
                .end()
                
                // Priority 2: Engage enemy in attack range
                .sequence("engage-behavior")
                    .node(HasTarget.create())
                    .node(IsTargetInRange.melee())
                    .task(createSetStateTask(InfantryState.ENGAGE))
                    .task(createMeleeAttackTask())
                .end()
                
                // Priority 3: Charge when in formation and enemies detected
                .sequence("charge-behavior")
                    .node(createHasEnemiesInChargeRange())
                    .node(createIsInFormation())
                    .task(createSetStateTask(InfantryState.CHARGE))
                    .task(createChargeTask())
                .end()
                
                // Priority 4: Pursue detected enemy
                .sequence("pursue-behavior")
                    .node(createHasTargetOrEnemiesNearby())
                    .task(createSetStateTask(InfantryState.MARCH))
                    .task(createPursueTask())
                .end()
                
                // Priority 5: Regroup when isolated
                .sequence("regroup-behavior")
                    .node(createIsIsolated())
                    .node(createHasVisibleAllies())
                    .task(createSetStateTask(InfantryState.REGROUP))
                    .task(createRegroupTask())
                .end()
                
                // Priority 6: Default patrol/hold
                .sequence("patrol-behavior")
                    .task(createSetStateTask(InfantryState.MARCH))
                    .task(new PatrolTask("infantry-patrol", 2f, 1.5f))
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
        
        // Update formation info
        updateFormationPerception();
        
        // Update attack range
        blackboard.setFloat(BlackboardKey.COMBAT_ATTACK_RANGE, owner.getAttackRange());
    }
    
    /**
     * Initializes patrol waypoints around spawn position.
     */
    private void initializePatrolWaypoints() {
        patrolWaypoints.clear();
        
        Vector3 home = new Vector3(owner.getPosition());
        float patrolRadius = 10f;
        
        // Square patrol pattern
        patrolWaypoints.add(new Vector3(home.x + patrolRadius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x + patrolRadius, home.y + patrolRadius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y + patrolRadius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y, home.z));
        
        blackboard.setList(BlackboardKey.MOVEMENT_PATROL_WAYPOINTS, patrolWaypoints);
        blackboard.setVector3(BlackboardKey.MOVEMENT_HOME_POSITION, home);
    }
    
    /**
     * Updates formation and ally perception data.
     */
    private void updateFormationPerception() {
        List<Entity> allies = new ArrayList<>();
        List<Entity> alliedInfantry = new ArrayList<>();
        Entity closestAlly = null;
        float closestDist = Float.MAX_VALUE;
        
        for (Entity e : context.getEntities()) {
            if (e == owner) continue;
            if (e.getTeam() != owner.getTeam()) continue;
            if (e instanceof Unit && ((Unit) e).isDead()) continue;
            
            float dist = owner.getPosition().dst(e.getPosition());
            
            if (dist <= ALLY_RANGE) {
                allies.add(e);
                
                if (e instanceof Infantry) {
                    alliedInfantry.add(e);
                }
                
                if (dist < closestDist) {
                    closestDist = dist;
                    closestAlly = e;
                }
            }
        }
        
        blackboard.setList(BlackboardKey.SOCIAL_NEARBY_ALLIES, allies);
        blackboard.setInt(BlackboardKey.CUSTOM_INT_1, alliedInfantry.size()); // Allied infantry count
        
        if (closestAlly != null) {
            blackboard.set(BlackboardKey.SOCIAL_SQUAD_LEADER, closestAlly);
            blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_2, closestDist);
        } else {
            blackboard.clear(BlackboardKey.SOCIAL_SQUAD_LEADER);
        }
        
        // Update the Infantry entity's ally count
        owner.updateNearbyAllies(context.getEntities());
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
    
    private ConditionNode createIsIsolated() {
        return new ConditionNode("isIsolated", bb -> {
            int allyCount = bb.getInt(BlackboardKey.CUSTOM_INT_1);
            return allyCount == 0;
        });
    }
    
    private ConditionNode createIsInFormation() {
        return new ConditionNode("isInFormation", bb -> {
            int allyCount = bb.getInt(BlackboardKey.CUSTOM_INT_1);
            return allyCount >= Infantry.MIN_ALLIES_FOR_BONUS;
        });
    }
    
    private ConditionNode createHasEnemiesInChargeRange() {
        return new ConditionNode("hasEnemiesInChargeRange", bb -> {
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            if (threats == null || threats.isEmpty()) return false;
            
            float closestDist = bb.getFloat(BlackboardKey.CUSTOM_FLOAT_1);
            return closestDist > 0 && closestDist <= CHARGE_RANGE && closestDist > owner.getAttackRange();
        });
    }
    
    private ConditionNode createHasTargetOrEnemiesNearby() {
        return new ConditionNode("hasTargetOrEnemiesNearby", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target != null && (!(target instanceof Unit) || !((Unit) target).isDead())) {
                return true;
            }
            
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            return threats != null && !threats.isEmpty();
        });
    }
    
    private ConditionNode createHasVisibleAllies() {
        return new ConditionNode("hasVisibleAllies", bb -> {
            // Check if there are any allies in extended range (for regrouping)
            for (Entity e : context.getEntities()) {
                if (e == owner) continue;
                if (e.getTeam() != owner.getTeam()) continue;
                if (e instanceof Unit && ((Unit) e).isDead()) continue;
                
                float dist = owner.getPosition().dst(e.getPosition());
                if (dist <= visionRange * 1.5f) { // Extended range for regroup
                    return true;
                }
            }
            return false;
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(InfantryState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createMeleeAttackTask() {
        return new TaskNode("meleeAttack", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                return NodeState.FAILURE;
            }
            
            // Face target
            Vector3 toTarget = new Vector3(target.getPosition()).sub(owner.getPosition());
            toTarget.z = 0;
            if (toTarget.len2() > 0.01f) {
                toTarget.nor();
                owner.getFacing().set(toTarget);
            }
            
            // Stop during attack
            owner.getVelocity().x = 0;
            owner.getVelocity().y = 0;
            
            // Attack with formation bonus damage
            if (owner.canAttack() && target instanceof Unit) {
                float damage = owner.getEffectiveDamage();
                owner.attack((Unit) target);
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createChargeTask() {
        return new TaskNode("charge", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                // Find new target from threats
                List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
                if (threats == null || threats.isEmpty()) {
                    return NodeState.FAILURE;
                }
                target = threats.get(0);
                bb.set(BlackboardKey.COMBAT_CURRENT_TARGET, target);
            }
            
            float dist = owner.getPosition().dst(target.getPosition());
            
            // Start charge if not already
            if (!owner.isCharging()) {
                owner.startCharge(3f); // 3 second charge
            }
            
            // In attack range - switch to engage
            if (dist <= owner.getAttackRange()) {
                return NodeState.SUCCESS;
            }
            
            // Charge toward target
            Vector3 direction = new Vector3(target.getPosition()).sub(owner.getPosition());
            direction.z = 0;
            direction.nor();
            
            float speed = owner.getMoveSpeed();
            owner.getVelocity().x = direction.x * speed;
            owner.getVelocity().y = direction.y * speed;
            owner.getFacing().set(direction);
            owner.setTargetPosition(target.getPosition());
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createPursueTask() {
        final float pursueSpeed = 5f;
        
        return new TaskNode("pursue", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                return NodeState.FAILURE;
            }
            
            float dist = owner.getPosition().dst(target.getPosition());
            
            // In attack range
            if (dist <= owner.getAttackRange()) {
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Move toward target
            Vector3 direction = new Vector3(target.getPosition()).sub(owner.getPosition());
            direction.z = 0;
            direction.nor();
            
            owner.getVelocity().x = direction.x * pursueSpeed;
            owner.getVelocity().y = direction.y * pursueSpeed;
            owner.getFacing().set(direction);
            owner.setTargetPosition(target.getPosition());
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createRetreatTask() {
        final float retreatSpeed = 4f;
        
        return new TaskNode("retreat", bb -> {
            Entity threat = bb.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
            
            // Find direction away from threat (or just backward)
            Vector3 retreatDir;
            if (threat != null) {
                retreatDir = new Vector3(owner.getPosition()).sub(threat.getPosition());
            } else {
                retreatDir = new Vector3(-owner.getFacing().x, -owner.getFacing().y, 0);
            }
            retreatDir.z = 0;
            retreatDir.nor();
            
            owner.getVelocity().x = retreatDir.x * retreatSpeed;
            owner.getVelocity().y = retreatDir.y * retreatSpeed;
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createRegroupTask() {
        final float regroupSpeed = 4.5f;
        
        return new TaskNode("regroup", bb -> {
            // Find nearest ally to regroup with
            Entity closestAlly = null;
            float closestDist = Float.MAX_VALUE;
            
            for (Entity e : context.getEntities()) {
                if (e == owner) continue;
                if (e.getTeam() != owner.getTeam()) continue;
                if (e instanceof Unit && ((Unit) e).isDead()) continue;
                
                float dist = owner.getPosition().dst(e.getPosition());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestAlly = e;
                }
            }
            
            if (closestAlly == null) {
                return NodeState.FAILURE;
            }
            
            // Already regrouped
            if (closestDist <= Infantry.FORMATION_SPACING * 2) {
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Move toward ally
            Vector3 direction = new Vector3(closestAlly.getPosition()).sub(owner.getPosition());
            direction.z = 0;
            direction.nor();
            
            owner.getVelocity().x = direction.x * regroupSpeed;
            owner.getVelocity().y = direction.y * regroupSpeed;
            owner.getFacing().set(direction);
            
            return NodeState.RUNNING;
        });
    }
}
