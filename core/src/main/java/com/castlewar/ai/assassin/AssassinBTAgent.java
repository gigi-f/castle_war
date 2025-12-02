package com.castlewar.ai.assassin;

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
import com.castlewar.entity.*;
import com.castlewar.simulation.TeamObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree-based AI agent for Assassin units.
 * <p>
 * Assassins have the following behavior priorities:
 * <ol>
 *   <li><b>Flee</b> - When spotted by guards and health is low</li>
 *   <li><b>Backstab</b> - When behind target and undetected</li>
 *   <li><b>Strike</b> - When close to King and opportunity exists</li>
 *   <li><b>Sneak</b> - Default infiltration behavior, avoid detection</li>
 * </ol>
 */
public class AssassinBTAgent extends TransitionableAgent<Assassin, AssassinState> {
    
    /** The target king to assassinate */
    private Entity targetKing;
    
    /** Current detection state */
    private boolean isSpotted;
    
    /** Detection timer (must remain hidden for this long) */
    private float stealthTimer;
    
    /** Distance to start strike behavior */
    private static final float STRIKE_RANGE = 8f;
    
    /** Detection range for guards */
    private static final float GUARD_DETECTION_RANGE = 12f;
    
    /**
     * Creates a new Assassin BT agent.
     * 
     * @param owner   The Assassin unit this agent controls
     * @param context Access to world state
     */
    public AssassinBTAgent(Assassin owner, AiContext context) {
        super(owner, context, AssassinState.class, AssassinState.IDLE);
        
        // Configure assassin-specific settings
        this.visionRange = 15f;
        this.fovDegrees = 140f;
        this.fleeHealthThreshold = 0.4f;
        
        // Initialize behavior tree
        initializeBehaviorTree();
    }
    
    /**
     * Sets the target king to assassinate.
     * 
     * @param king The target king
     */
    public void setTargetKing(Entity king) {
        this.targetKing = king;
        blackboard.set(BlackboardKey.GOAL_PRIMARY_TARGET, king);
    }
    
    @Override
    protected BehaviorTree createBehaviorTree() {
        return BehaviorTree.builder("assassin-bt")
            .selector("root")
                // Priority 1: Flee when spotted and low health
                .sequence("flee-behavior")
                    .condition("isSpottedAndLowHealth", this::isSpottedAndLowHealth)
                    .task(createSetStateTask(AssassinState.FLEE))
                    .task(createAssassinFleeTask())
                .end()
                
                // Priority 2: Backstab opportunity
                .sequence("backstab-behavior")
                    .condition("hasBackstabTarget", this::hasBackstabTarget)
                    .task(createSetStateTask(AssassinState.STRIKE))
                    .task(createBackstabTask())
                .end()
                
                // Priority 3: Strike king when close
                .sequence("strike-behavior")
                    .condition("kingInRange", this::kingInRange)
                    .task(createSetStateTask(AssassinState.STRIKE))
                    .task(createStrikeKingTask())
                .end()
                
                // Priority 4: Sneak and infiltrate
                .sequence("sneak-behavior")
                    .task(createSetStateTask(AssassinState.SNEAK))
                    .task(createInfiltrateTask())
                .end()
            .end()
            .build();
    }
    
    @Override
    protected void updateBlackboard(float delta) {
        super.updateBlackboard(delta);
        
        // Store delta time
        blackboard.setFloat(BlackboardKey.CUSTOM_1, delta);
        
        // Auto-acquire target king from TeamObjective if not explicitly set
        if (targetKing == null || (targetKing instanceof Unit && ((Unit) targetKing).isDead())) {
            King enemyKing = TeamObjective.getInstance().getEnemyKing(owner.getTeam());
            if (enemyKing != null && !enemyKing.isDead()) {
                targetKing = enemyKing;
            }
        }
        
        // Update target king
        if (targetKing != null) {
            blackboard.set(BlackboardKey.GOAL_PRIMARY_TARGET, targetKing);
            blackboard.setVector3(BlackboardKey.MOVEMENT_TARGET_POSITION, targetKing.getPosition());
        }
        
        // Update stealth timer
        if (stealthTimer > 0) {
            stealthTimer = Math.max(0f, stealthTimer - delta);
            if (stealthTimer <= 0) {
                isSpotted = false;
            }
        }
        
        blackboard.setBool(BlackboardKey.CUSTOM_BOOL_1, isSpotted); // IS_SPOTTED
    }
    
    @Override
    protected void updatePerception(List<Entity> entities) {
        super.updatePerception(entities);
        
        // Check if spotted by guards
        checkForSpotting(entities);
        
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
            
            // Guards and other hostiles
            if (unit instanceof Guard) {
                if (dist <= GUARD_DETECTION_RANGE) {
                    threats.add(unit);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestThreat = unit;
                    }
                }
            }
        }
        
        blackboard.setList(BlackboardKey.THREAT_ENEMIES_IN_RANGE, threats);
        if (nearestThreat != null) {
            blackboard.set(BlackboardKey.THREAT_NEAREST_ENEMY, nearestThreat);
        }
        
        // Check for backstab opportunities
        updateBackstabOpportunities(entities);
    }
    
    /**
     * Checks if the assassin has been spotted by enemies.
     */
    private void checkForSpotting(List<Entity> entities) {
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            // Check if guard can see us
            if (unit instanceof Guard) {
                float dist = owner.getPosition().dst(unit.getPosition());
                if (dist > GUARD_DETECTION_RANGE) continue;
                
                // Check if guard is facing us
                Vector3 guardToAssassin = new Vector3(owner.getPosition()).sub(unit.getPosition());
                guardToAssassin.z = 0;
                guardToAssassin.nor();
                
                float dot = unit.getFacing().dot(guardToAssassin);
                boolean inFOV = dot > 0.5f; // ~120 degree FOV
                
                // Stealth detection is harder at range
                float detectionChance = inFOV ? (1f - (dist / GUARD_DETECTION_RANGE) * 0.5f) : 0.1f;
                
                if (dist < 2f || (inFOV && detectionChance > 0.5f)) {
                    isSpotted = true;
                    stealthTimer = 3f;
                    blackboard.setBool(BlackboardKey.THREAT_UNDER_ATTACK, true);
                    return;
                }
            }
        }
    }
    
    /**
     * Looks for backstab opportunities against unaware targets.
     */
    private void updateBackstabOpportunities(List<Entity> entities) {
        Entity backstabTarget = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        
        for (Entity e : entities) {
            if (!(e instanceof Unit)) continue;
            Unit unit = (Unit) e;
            
            if (unit.getTeam() == owner.getTeam()) continue;
            if (unit.isDead()) continue;
            
            float dist = owner.getPosition().dst(unit.getPosition());
            if (dist > 2f) continue; // Must be close for backstab
            
            // Check if we're behind them
            Vector3 targetToUs = new Vector3(owner.getPosition()).sub(unit.getPosition());
            targetToUs.z = 0;
            targetToUs.nor();
            
            float dot = unit.getFacing().dot(targetToUs);
            if (dot > -0.3f) continue; // Must be behind (facing away)
            
            // Score based on how far behind and target value
            float behindScore = -dot; // Higher when more behind
            float valueScore = (unit instanceof King) ? 2f : 1f;
            float score = behindScore * valueScore;
            
            if (score > bestScore) {
                bestScore = score;
                backstabTarget = unit;
            }
        }
        
        if (backstabTarget != null) {
            blackboard.set(BlackboardKey.CUSTOM_ENTITY_1, backstabTarget); // BACKSTAB_TARGET
        } else {
            blackboard.clear(BlackboardKey.CUSTOM_ENTITY_1);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITION PREDICATES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private boolean isSpottedAndLowHealth(Blackboard bb) {
        boolean spotted = bb.getBool(BlackboardKey.CUSTOM_BOOL_1);
        if (!spotted) return false;
        
        float hpPercent = bb.getFloat(BlackboardKey.SELF_HP_PERCENT);
        return hpPercent <= fleeHealthThreshold;
    }
    
    private boolean hasBackstabTarget(Blackboard bb) {
        Entity target = bb.get(BlackboardKey.CUSTOM_ENTITY_1, Entity.class);
        if (target == null) return false;
        return !(target instanceof Unit) || !((Unit) target).isDead();
    }
    
    private boolean kingInRange(Blackboard bb) {
        Entity king = bb.get(BlackboardKey.GOAL_PRIMARY_TARGET, Entity.class);
        if (king == null) return false;
        if (king instanceof Unit && ((Unit) king).isDead()) return false;
        
        float dist = owner.getPosition().dst(king.getPosition());
        return dist <= STRIKE_RANGE;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TASK FACTORIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    private TaskNode createSetStateTask(AssassinState state) {
        return new TaskNode("setState-" + state.name(), bb -> {
            changeState(state);
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createAssassinFleeTask() {
        final float fleeSpeed = 10f;
        
        return new TaskNode("assassinFlee", bb -> {
            Entity threat = bb.get(BlackboardKey.THREAT_NEAREST_ENEMY, Entity.class);
            if (threat == null) {
                // No threat, stop fleeing
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.SUCCESS;
            }
            
            // Calculate flee direction (away from threat)
            Vector3 fleeDir = new Vector3(owner.getPosition()).sub(threat.getPosition());
            fleeDir.z = 0;
            
            // Add random tangent for unpredictability
            float tangentStrength = MathUtils.random(0.3f, 0.5f);
            Vector3 tangent = new Vector3(-fleeDir.y, fleeDir.x, 0f).scl(MathUtils.randomSign() * tangentStrength);
            fleeDir.add(tangent);
            
            if (!fleeDir.isZero(0.001f)) {
                fleeDir.nor();
                owner.getVelocity().x = fleeDir.x * fleeSpeed;
                owner.getVelocity().y = fleeDir.y * fleeSpeed;
                owner.getFacing().set(fleeDir);
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private TaskNode createBackstabTask() {
        return new TaskNode("backstab", bb -> {
            Entity target = bb.get(BlackboardKey.CUSTOM_ENTITY_1, Entity.class);
            if (target == null) return NodeState.FAILURE;
            if (target instanceof Unit && ((Unit) target).isDead()) return NodeState.FAILURE;
            
            Unit targetUnit = (Unit) target;
            
            // Face target
            Vector3 toTarget = new Vector3(target.getPosition()).sub(owner.getPosition());
            toTarget.z = 0;
            if (!toTarget.isZero(0.01f)) {
                toTarget.nor();
                owner.getFacing().set(toTarget);
            }
            
            // Stop movement
            owner.getVelocity().x = 0;
            owner.getVelocity().y = 0;
            
            // Execute backstab
            owner.attack(targetUnit);
            
            // Clear backstab target
            bb.clear(BlackboardKey.CUSTOM_ENTITY_1);
            
            return NodeState.SUCCESS;
        });
    }
    
    private TaskNode createStrikeKingTask() {
        final float approachSpeed = 8f;
        
        return new TaskNode("strikeKing", bb -> {
            Entity king = bb.get(BlackboardKey.GOAL_PRIMARY_TARGET, Entity.class);
            if (king == null) return NodeState.FAILURE;
            if (king instanceof Unit && ((Unit) king).isDead()) return NodeState.FAILURE;
            
            float dist = owner.getPosition().dst(king.getPosition());
            float attackRange = 1.5f;
            
            if (dist <= attackRange) {
                // In attack range - strike!
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                
                // Face king
                Vector3 toKing = new Vector3(king.getPosition()).sub(owner.getPosition());
                toKing.z = 0;
                if (!toKing.isZero(0.01f)) {
                    toKing.nor();
                    owner.getFacing().set(toKing);
                }
                
                if (king instanceof Unit) {
                    owner.attack((Unit) king);
                }
                
                return NodeState.RUNNING;
            } else {
                // Approach king
                Vector3 direction = new Vector3(king.getPosition()).sub(owner.getPosition());
                direction.z = 0;
                direction.nor();
                
                owner.getVelocity().x = direction.x * approachSpeed;
                owner.getVelocity().y = direction.y * approachSpeed;
                owner.getFacing().set(direction);
                
                return NodeState.RUNNING;
            }
        });
    }
    
    private TaskNode createInfiltrateTask() {
        final float sneakSpeed = 4f;
        
        return new TaskNode("infiltrate", bb -> {
            Entity king = bb.get(BlackboardKey.GOAL_PRIMARY_TARGET, Entity.class);
            if (king == null) {
                // No target - idle
                owner.getVelocity().x = 0;
                owner.getVelocity().y = 0;
                return NodeState.RUNNING;
            }
            
            // Get threats
            List<Entity> threats = bb.getList(BlackboardKey.THREAT_ENEMIES_IN_RANGE);
            boolean hasThreats = threats != null && !threats.isEmpty();
            
            Vector3 targetPos = king.getPosition();
            Vector3 myPos = owner.getPosition();
            
            // Calculate direction to target
            Vector3 toTarget = new Vector3(targetPos).sub(myPos);
            toTarget.z = 0;
            
            if (hasThreats) {
                // Adjust path to avoid threats
                Vector3 avoidance = calculateAvoidanceDirection(threats);
                if (!avoidance.isZero(0.01f)) {
                    // Blend target direction with avoidance
                    toTarget.nor().scl(0.6f);
                    avoidance.scl(0.4f);
                    toTarget.add(avoidance);
                }
            }
            
            if (!toTarget.isZero(0.01f)) {
                toTarget.nor();
                owner.getVelocity().x = toTarget.x * sneakSpeed;
                owner.getVelocity().y = toTarget.y * sneakSpeed;
                owner.getFacing().set(toTarget);
            }
            
            return NodeState.RUNNING;
        });
    }
    
    private Vector3 calculateAvoidanceDirection(List<Entity> threats) {
        Vector3 avoidance = new Vector3();
        
        for (Entity threat : threats) {
            Vector3 away = new Vector3(owner.getPosition()).sub(threat.getPosition());
            away.z = 0;
            float dist = away.len();
            if (dist > 0.1f) {
                away.nor();
                // Weight by inverse distance
                away.scl(1f / (dist + 1f));
                avoidance.add(away);
            }
        }
        
        if (!avoidance.isZero(0.01f)) {
            avoidance.nor();
        }
        
        return avoidance;
    }
}
