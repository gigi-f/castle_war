package com.castlewar.ai.siege;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.TransitionableAgent;
import com.castlewar.ai.behavior.BehaviorTree;
import com.castlewar.ai.behavior.NodeState;
import com.castlewar.ai.behavior.leaf.ConditionNode;
import com.castlewar.ai.behavior.leaf.TaskNode;
import com.castlewar.ai.behavior.leaf.tasks.PatrolTask;
import com.castlewar.ai.blackboard.BlackboardKey;
import com.castlewar.entity.Entity;
import com.castlewar.entity.Team;
import com.castlewar.entity.Trebuchet;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Behavior Tree agent for Trebuchet siege engine.
 * 
 * Behavior priorities:
 * 1. Flee if enemies too close (can't defend itself)
 * 2. Fire at target when aimed and ready
 * 3. Aim at valid target
 * 4. Setup if just relocated
 * 5. Relocate if no valid targets in range
 * 6. Idle and await orders
 * 
 * Siege engines prioritize structures over units.
 */
public class TrebuchetBTAgent extends TransitionableAgent<Trebuchet, TrebuchetState> {
    
    /** Distance at which enemies are considered dangerous (too close) */
    private static final float DANGER_RADIUS = 8f;
    
    /** Preferred distance from target for optimal firing */
    private static final float OPTIMAL_RANGE = 25f;
    
    /** Time to stay idle before attempting relocate */
    private static final float IDLE_RELOCATE_TIME = 5f;
    
    /** Target position for firing */
    private Vector3 targetPosition = new Vector3();
    
    /** Cached idle time */
    private float idleTime = 0f;
    
    /**
     * Creates a new Trebuchet BT agent.
     * 
     * @param owner   The Trebuchet this agent controls
     * @param context Access to world state
     */
    public TrebuchetBTAgent(Trebuchet owner, AiContext context) {
        super(owner, context, TrebuchetState.class, TrebuchetState.IDLE);
        
        // Trebuchets have long range vision for targeting
        this.visionRange = Trebuchet.MAX_RANGE;
        this.fovDegrees = 360f; // Can see in all directions
        this.fleeHealthThreshold = 0.2f;
        
        initializeBehaviorTree();
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("trebuchet-bt")
            .selector("root")
                // Priority 1: Flee if enemies too close
                .sequence("flee-behavior")
                    .node(createEnemyTooClose())
                    .task(createSetStateTask(TrebuchetState.RELOCATE))
                    .task(createFleeTask())
                .end()
                
                // Priority 2: Fire at target when ready
                .sequence("fire-behavior")
                    .node(createCanFire())
                    .node(createHasAimedTarget())
                    .task(createSetStateTask(TrebuchetState.FIRE))
                    .task(createFireTask())
                .end()
                
                // Priority 3: Aim at target
                .sequence("aim-behavior")
                    .node(createIsSetUp())
                    .node(createHasAmmo())
                    .node(createHasValidTarget())
                    .task(createSetStateTask(TrebuchetState.AIM))
                    .task(createAimTask())
                .end()
                
                // Priority 4: Setup after relocating
                .sequence("setup-behavior")
                    .node(createNeedsSetup())
                    .task(createSetStateTask(TrebuchetState.IDLE))
                    .task(createSetupTask())
                .end()
                
                // Priority 5: Relocate if idle too long
                .sequence("relocate-behavior")
                    .node(createIdleTooLong())
                    .task(createSetStateTask(TrebuchetState.RELOCATE))
                    .task(createRelocateTask())
                .end()
                
                // Priority 6: Default idle
                .sequence("idle-behavior")
                    .task(createSetStateTask(TrebuchetState.IDLE))
                    .task(createIdleTask())
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Update trebuchet-specific data
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Track idle time
        if (owner.getState() == TrebuchetState.IDLE) {
            idleTime += delta;
        } else {
            idleTime = 0f;
        }
        blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_1, idleTime);
        
        // Find closest enemy distance
        float closestEnemyDist = Float.MAX_VALUE;
        for (Entity e : context.getEntities()) {
            if (e instanceof Unit && ((Unit) e).getTeam() != owner.getTeam()) {
                Unit unit = (Unit) e;
                if (!unit.isDead()) {
                    float dist = owner.getPosition().dst(e.getPosition());
                    if (dist < closestEnemyDist) {
                        closestEnemyDist = dist;
                    }
                }
            }
        }
        blackboard.setFloat(BlackboardKey.CUSTOM_FLOAT_2, closestEnemyDist);
        
        // Update attack range
        blackboard.setFloat(BlackboardKey.COMBAT_ATTACK_RANGE, Trebuchet.MAX_RANGE);
    }
    
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        
        // Build threat list (all visible enemies in range)
        List<Entity> threats = new ArrayList<>();
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist > Trebuchet.MAX_RANGE) continue;
            
            threats.add(unit);
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        
        // Find best target (prioritize clusters)
        Entity bestTarget = findBestTarget(threats);
        if (bestTarget != null) {
            blackboard.set(BlackboardKey.COMBAT_CURRENT_TARGET, bestTarget);
            targetPosition.set(bestTarget.getPosition());
        }
    }
    
    @Override
    protected float getAttackRange() {
        return Trebuchet.MAX_RANGE;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITION FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private ConditionNode createEnemyTooClose() {
        return new ConditionNode("enemyTooClose", bb -> {
            float closestDist = bb.getFloat(BlackboardKey.CUSTOM_FLOAT_2);
            return closestDist < DANGER_RADIUS;
        });
    }
    
    private ConditionNode createCanFire() {
        return new ConditionNode("canFire", bb -> {
            return owner.canFire();
        });
    }
    
    private ConditionNode createHasAimedTarget() {
        return new ConditionNode("hasAimedTarget", bb -> {
            return owner.isAimed() && targetPosition.len2() > 0;
        });
    }
    
    private ConditionNode createIsSetUp() {
        return new ConditionNode("isSetUp", bb -> {
            return owner.isSetUp();
        });
    }
    
    private ConditionNode createHasAmmo() {
        return new ConditionNode("hasAmmo", bb -> {
            return owner.hasAmmo();
        });
    }
    
    private ConditionNode createHasValidTarget() {
        return new ConditionNode("hasValidTarget", bb -> {
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            if (threats == null || threats.isEmpty()) return false;
            
            // Check if any threat is in valid range
            for (Entity e : threats) {
                if (owner.isInRange(e.getPosition().x, e.getPosition().y)) {
                    return true;
                }
            }
            return false;
        });
    }
    
    private ConditionNode createNeedsSetup() {
        return new ConditionNode("needsSetup", bb -> {
            return !owner.isSetUp();
        });
    }
    
    private ConditionNode createIdleTooLong() {
        return new ConditionNode("idleTooLong", bb -> {
            float idle = bb.getFloat(BlackboardKey.CUSTOM_FLOAT_1);
            return idle > IDLE_RELOCATE_TIME;
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(TrebuchetState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createFleeTask() {
        return new TaskNode("flee", bb -> {
            owner.startRelocating();
            
            // Find safest direction (away from closest enemy)
            Vector3 fleeDir = findFleeDirection();
            float delta = bb.getFloat(BlackboardKey.CUSTOM_1);
            float speed = Trebuchet.MOVE_SPEED * 1.5f; // Slightly faster when fleeing
            
            Vector3 pos = owner.getPosition();
            pos.x += fleeDir.x * speed * delta;
            pos.y += fleeDir.y * speed * delta;
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createFireTask() {
        return new TaskNode("fire", bb -> {
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null) return NodeState.FAILURE;
            
            float targetX = target.getPosition().x;
            float targetY = target.getPosition().y;
            
            if (owner.fire(targetX, targetY)) {
                // Apply splash damage
                applySplashDamage(targetX, targetY);
                return NodeState.SUCCESS;
            }
            
            return NodeState.FAILURE;
        });
    }
    
    private TaskNode createAimTask() {
        return new TaskNode("aim", bb -> {
            if (!owner.isSetUp() || !owner.hasAmmo()) return NodeState.FAILURE;
            
            Entity target = bb.get(BlackboardKey.COMBAT_CURRENT_TARGET, Entity.class);
            if (target == null) return NodeState.FAILURE;
            
            // Update target position
            targetPosition.set(target.getPosition());
            
            if (owner.getState() != TrebuchetState.AIM) {
                owner.startAiming();
            }
            
            if (owner.isAimed()) {
                return NodeState.SUCCESS;
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createSetupTask() {
        return new TaskNode("setup", bb -> {
            if (owner.isSetUp()) return NodeState.SUCCESS;
            
            owner.beginSetup();
            
            if (owner.getSetupProgress() >= 1f) {
                return NodeState.SUCCESS;
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createRelocateTask() {
        return new TaskNode("relocate", bb -> {
            owner.startRelocating();
            
            // Move toward firing position
            Vector3 targetPos = findFiringPosition();
            Vector3 pos = owner.getPosition();
            
            float dx = targetPos.x - pos.x;
            float dy = targetPos.y - pos.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist < 2f) {
                owner.beginSetup();
                idleTime = 0f;
                return NodeState.SUCCESS;
            }
            
            float delta = bb.getFloat(BlackboardKey.CUSTOM_1);
            float speed = Trebuchet.MOVE_SPEED;
            
            pos.x += (dx / dist) * speed * delta;
            pos.y += (dy / dist) * speed * delta;
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createIdleTask() {
        return new TaskNode("idle", bb -> {
            // Just idle - state already set
            return NodeState.SUCCESS;
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Vector3 findFleeDirection() {
        Vector3 fleeDir = new Vector3();
        int count = 0;
        
        for (Entity e : context.getEntities()) {
            if (e instanceof Unit && ((Unit) e).getTeam() != owner.getTeam()) {
                Unit unit = (Unit) e;
                if (unit.isDead()) continue;
                
                float dist = owner.getPosition().dst(e.getPosition());
                if (dist < DANGER_RADIUS * 2) {
                    float dx = owner.getPosition().x - e.getPosition().x;
                    float dy = owner.getPosition().y - e.getPosition().y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0) {
                        fleeDir.x += dx / len;
                        fleeDir.y += dy / len;
                        count++;
                    }
                }
            }
        }
        
        if (count > 0) {
            fleeDir.x /= count;
            fleeDir.y /= count;
            fleeDir.nor();
        } else {
            // Default: move toward own side
            fleeDir.x = owner.getTeam() == Team.WHITE ? -1 : 1;
        }
        
        return fleeDir;
    }
    
    private Entity findBestTarget(List<Entity> threats) {
        if (threats == null || threats.isEmpty()) return null;
        
        // Filter to valid range
        List<Entity> validTargets = new ArrayList<>();
        for (Entity e : threats) {
            if (owner.isInRange(e.getPosition().x, e.getPosition().y)) {
                validTargets.add(e);
            }
        }
        
        if (validTargets.isEmpty()) return null;
        
        // Prioritize targets with most enemies in splash radius
        return validTargets.stream()
            .max(Comparator.comparingInt(e -> countEnemiesInRadius(e.getPosition(), validTargets)))
            .orElse(null);
    }
    
    private int countEnemiesInRadius(Vector3 center, List<Entity> enemies) {
        int count = 0;
        for (Entity e : enemies) {
            if (center.dst(e.getPosition()) <= Trebuchet.SPLASH_RADIUS) {
                count++;
            }
        }
        return count;
    }
    
    private Vector3 findFiringPosition() {
        // Find centroid of enemies
        float centerX = 0, centerY = 0;
        int count = 0;
        
        for (Entity e : context.getEntities()) {
            if (e instanceof Unit && ((Unit) e).getTeam() != owner.getTeam()) {
                Unit unit = (Unit) e;
                if (!unit.isDead()) {
                    centerX += e.getPosition().x;
                    centerY += e.getPosition().y;
                    count++;
                }
            }
        }
        
        if (count == 0) {
            // No enemies, stay in place
            return owner.getPosition();
        }
        
        centerX /= count;
        centerY /= count;
        
        // Position at optimal range toward enemy center
        float dx = owner.getPosition().x - centerX;
        float dy = owner.getPosition().y - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (dist > 0) {
            return new Vector3(
                centerX + (dx / dist) * OPTIMAL_RANGE,
                centerY + (dy / dist) * OPTIMAL_RANGE,
                owner.getPosition().z
            );
        }
        
        return owner.getPosition();
    }
    
    private void applySplashDamage(float targetX, float targetY) {
        float splashRadius = Trebuchet.SPLASH_RADIUS;
        float damage = Trebuchet.SIEGE_DAMAGE;
        
        for (Entity e : context.getEntities()) {
            if (e instanceof Unit && ((Unit) e).getTeam() != owner.getTeam()) {
                Unit unit = (Unit) e;
                if (unit.isDead()) continue;
                
                float dx = unit.getPosition().x - targetX;
                float dy = unit.getPosition().y - targetY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (dist <= splashRadius) {
                    // Damage falls off with distance
                    float falloff = 1f - (dist / splashRadius);
                    unit.takeDamage(damage * falloff);
                }
            }
        }
    }
}
