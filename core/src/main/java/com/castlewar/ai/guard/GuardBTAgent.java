package com.castlewar.ai.guard;

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
import com.castlewar.entity.Guard;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for Guard units.
 * <p>
 * Guards have the following behavior priorities (evaluated via Selector):
 * <ol>
 *   <li><b>Flee</b> - When low health and not near allies</li>
 *   <li><b>Engage</b> - When enemy in range, attack</li>
 *   <li><b>Alert</b> - When enemy detected, move to engage</li>
 *   <li><b>Patrol</b> - Default behavior, walk waypoints</li>
 * </ol>
 */
public class GuardBTAgent extends TransitionableAgent<Guard, GuardState> {
    
    /** Patrol waypoints for this guard */
    private final List<Vector3> patrolWaypoints = new ArrayList<>();
    
    /** Whether patrol waypoints have been initialized */
    private boolean waypointsInitialized = false;
    
    /**
     * Creates a new Guard BT agent.
     * 
     * @param owner   The Guard unit this agent controls
     * @param context Access to world state
     */
    public GuardBTAgent(Guard owner, AiContext context) {
        super(owner, context, GuardState.class, GuardState.IDLE);
        
        // Configure guard-specific settings
        this.visionRange = 12f;
        this.fovDegrees = 120f;
        this.fleeHealthThreshold = 0.3f;
        
        // Initialize behavior tree after configuration
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("guard-bt")
            .selector("root")
                // Priority 1: Flee when low health and isolated
                .sequence("flee-behavior")
                    .node(IsLowHealth.standard())
                    .node(createNotAlliesNearby())
                    .task(createSetStateTask(GuardState.FLEE))
                    .task(new FleeTask())
                .end()
                
                // Priority 2: Engage enemy in attack range
                .sequence("engage-behavior")
                    .node(HasTarget.create())
                    .node(IsTargetInRange.melee())
                    .task(createSetStateTask(GuardState.ENGAGE))
                    .task(createGuardAttackTask())
                .end()
                
                // Priority 3: Alert - pursue detected enemy
                .sequence("alert-behavior")
                    .node(createHasTargetOrEnemiesNearby())
                    .task(createSetStateTask(GuardState.ALERT))
                    .task(createPursueTargetTask())
                .end()
                
                // Priority 4: Patrol (default behavior)
                .sequence("patrol-behavior")
                    .task(createSetStateTask(GuardState.PATROL))
                    .task(new PatrolTask("guard-patrol", 2f, 2f))
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Initialize patrol waypoints if not done
        if (!waypointsInitialized) {
            initializePatrolWaypoints();
            waypointsInitialized = true;
        }
        
        // Store delta time for tasks
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Update attack range
        blackboard.setFloat(BlackboardKey.COMBAT_ATTACK_RANGE, owner.getAttackRange());
        
        // Update nearby allies count
        updateAllyPerception();
    }
    
    /**
     * Initializes patrol waypoints based on guard type and position.
     */
    private void initializePatrolWaypoints() {
        patrolWaypoints.clear();
        
        // Create waypoints around spawn position
        Vector3 home = new Vector3(owner.getPosition());
        float patrolRadius = 8f;
        
        // Square patrol pattern
        patrolWaypoints.add(new Vector3(home.x + patrolRadius, home.y, home.z));
        patrolWaypoints.add(new Vector3(home.x + patrolRadius, home.y + patrolRadius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y + patrolRadius, home.z));
        patrolWaypoints.add(new Vector3(home.x, home.y, home.z));
        
        // Store in blackboard
        blackboard.setList(BlackboardKey.MOVEMENT_PATROL_WAYPOINTS, patrolWaypoints);
        blackboard.setVector3(BlackboardKey.MOVEMENT_HOME_POSITION, home);
    }
    
    /**
     * Updates nearby ally information in blackboard.
     */
    private void updateAllyPerception() {
        List<Entity> allies = new ArrayList<>();
        float allyRange = 10f;
        
        for (Entity e : context.getEntities()) {
            if (e == owner) continue;
            if (e.getTeam() != owner.getTeam()) continue;
            if (e instanceof Unit && ((Unit) e).isDead()) continue;
            
            float dist = owner.getPosition().dst(e.getPosition());
            if (dist <= allyRange) {
                allies.add(e);
            }
        }
        
        blackboard.setList(BlackboardKey.SOCIAL_NEARBY_ALLIES, allies);
    }
    
    /**
     * Updates enemy perception, including threat list.
     */
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        
        // Build list of all visible enemies for threat assessment
        List<Entity> threats = new ArrayList<>();
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist > visionRange) continue;
            
            // FOV check
            if (!isInFieldOfView(unit)) continue;
            
            threats.add(unit);
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        
        // Set nearest enemy as threat for flee calculations
        Entity currentTarget = blackboard.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
        if (currentTarget != null) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, currentTarget);
        } else if (!threats.isEmpty()) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, threats.get(0));
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
     * Creates a condition that succeeds when NO allies are nearby.
     */
    private ConditionNode createNotAlliesNearby() {
        return new ConditionNode("notAlliesNearby", bb -> {
            List<Entity> allies = bb.getList(BlackboardKey.SOCIAL_NEARBY_ALLIES);
            return allies == null || allies.isEmpty();
        });
    }
    
    /**
     * Creates a condition that succeeds when target exists OR enemies nearby.
     */
    private ConditionNode createHasTargetOrEnemiesNearby() {
        return new ConditionNode("hasTargetOrEnemiesNearby", bb -> {
            // Check for current target
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target != null && (!(target instanceof Unit) || !((Unit) target).isDead())) {
                return true;
            }
            
            // Check for nearby enemies
            List<Entity> enemies = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            if (enemies != null && !enemies.isEmpty()) {
                // Pick closest enemy as new target
                Entity closest = findClosestEnemy(enemies);
                if (closest != null) {
                    bb.set(BlackboardKey.COMBAT_CURRENT_TARGET, closest);
                    return true;
                }
            }
            
            return false;
        });
    }
    
    private Entity findClosestEnemy(List<Entity> enemies) {
        Entity closest = null;
        float closestDist = Float.MAX_VALUE;
        
        for (Entity e : enemies) {
            if (e instanceof Unit && ((Unit) e).isDead()) continue;
            float dist = owner.getPosition().dst(e.getPosition());
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        
        return closest;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a task that sets the FSM state for animation purposes.
     */
    private TaskNode createSetStateTask(GuardState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    /**
     * Creates task for guard attack behavior with facing adjustment.
     */
    private TaskNode createGuardAttackTask() {
        return new TaskNode("guardAttack", bb -> {
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
            
            // Stop movement during attack
            owner.getVelocity().x = 0;
            owner.getVelocity().y = 0;
            
            // Execute attack (handled by Guard.updateAttack())
            owner.updateAttack();
            
            return NodeState.RUNNING;
        });
    }
    
    /**
     * Creates task for pursuing a target enemy.
     */
    private TaskNode createPursueTargetTask() {
        final float pursueSpeed = 5f;
        
        return new TaskNode("pursueTarget", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null || (target instanceof Unit && ((Unit) target).isDead())) {
                return NodeState.FAILURE;
            }
            
            Vector3 targetPos = target.getPosition();
            Vector3 myPos = owner.getPosition();
            float distance = myPos.dst(targetPos);
            
            // Check if in attack range
            float attackRange = bb.getFloat(BlackboardKey.COMBAT_ATTACK_RANGE);
            if (distance <= attackRange) {
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Move toward target
            Vector3 direction = new Vector3(targetPos).sub(myPos);
            direction.z = 0;
            direction.nor();
            
            owner.getVelocity().x = direction.x * pursueSpeed;
            owner.getVelocity().y = direction.y * pursueSpeed;
            owner.getFacing().set(direction);
            owner.setTargetPosition(targetPos);
            
            return NodeState.RUNNING;
        });
    }
}
