package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.assassin.AssassinBTAgent;
import com.castlewar.ai.assassin.AssassinState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;
import java.util.ArrayList;
import java.util.List;

public class Assassin extends Unit {
    private static final String[] NAMES = {"Shadow", "Ghost", "Viper", "Phantom", "Wraith", "Blade"};
    private static final float MIN_ASSASSIN_DISTANCE = 12f;
    private static final float SAFE_EXPOSURE_THRESHOLD = 0.35f;
    private static final float BASE_SPEED = 8.0f;
    
    private Entity targetKing;
    private float moveTimer = 0f;
    // targetPosition is inherited from Unit - don't redeclare it!
    private boolean isFleeing = false;
    private float stealthTimer = 0f;
    private final Vector3 threatDirection = new Vector3();
    private final List<Entity> visibleThreats = new ArrayList<>();
    private final Vector3 preferredDirection = new Vector3();
    private boolean isSpacing = false;
    private Unit nearestAssassinThreat;
    private float nearestAssassinDistance = Float.MAX_VALUE;
    private final AssassinBTAgent aiAgent;
    private transient float aiDeltaSnapshot;

    public Assassin(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), 30f, 40f);
        this.canClimb = true;
        this.aiAgent = new AssassinBTAgent(this, new AiContext(worldContext));
    }

    private static String generateName() {
        return NAMES[MathUtils.random(NAMES.length - 1)];
    }

    public void setTargetKing(Entity king) {
        this.targetKing = king;
    }

    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (!beginUpdate(delta, world)) {
            return;
        }

        this.aiDeltaSnapshot = delta;
        aiAgent.update(delta, aiAgent.getContext());

        super.applyPhysics(delta, world);
        resolvePostPhysicsAttacks();
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 5.0f;
    }

    private void resolvePostPhysicsAttacks() {
        AiContext ctx = aiAgent.getContext();
        
        // Prioritize backstab opportunities - but not while fleeing!
        if (!isFleeing && targetEnemy != null && canBackstab(targetEnemy, ctx.getEntities())) {
            executeBackstab(targetEnemy);
            return;
        }
        
        if (targetKing instanceof Unit) {
            Unit kingUnit = (Unit) targetKing;
            if (!isFleeing && !kingUnit.isDead() && canBackstab(kingUnit, ctx.getEntities())) {
                executeBackstab(kingUnit);
                return;
            }
        }
        
        // Normal attacks
        if (targetKing instanceof Unit && !((Unit) targetKing).isDead()) {
            if (position.dst(targetKing.getPosition()) < attackRange) {
                attack((Unit) targetKing);
            }
        }

        if (attackTimer <= 0f && targetEnemy != null && !targetEnemy.isDead()) {
            if (position.dst(targetEnemy.getPosition()) < attackRange) {
                attack(targetEnemy);
            }
        }
    }

    public boolean hasStrikeOpportunity() {
        if (targetEnemy != null && !targetEnemy.isDead()) {
            return true;
        }
        return targetKing instanceof Unit && !((Unit) targetKing).isDead() && position.dst(targetKing.getPosition()) < 12f;
    }

    public boolean shouldFlee() {
        return isFleeing || hp < maxHp * 0.4f;
    }

    public AssassinBTAgent getAiAgent() {
        return aiAgent;
    }

    public float getAiDeltaSnapshot() {
        return aiDeltaSnapshot;
    }

    public void changeState(AssassinState nextState) {
        aiAgent.changeState(nextState);
    }
    
    public void checkForGuards(List<Entity> entities, GridWorld world, float delta) {
        if (targetKing != null && position.dst(targetKing.getPosition()) < 8f) {
            isFleeing = false;
            stealthTimer = 0f;
            visibleThreats.clear();
            threatDirection.setZero();
            return;
        }

        boolean spotted = false;
        visibleThreats.clear();
        threatDirection.setZero();
        nearestAssassinThreat = null;
        nearestAssassinDistance = Float.MAX_VALUE;

        for (Entity e : entities) {
            if (!(e instanceof Unit) || e == this) {
                continue;
            }
            Unit other = (Unit) e;
            if (other.getTeam() == this.team || other.isDead()) {
                continue;
            }

            float dist = position.dst(other.getPosition());
            float detectionRange = getDetectionRangeFor(other);
            if (other instanceof Assassin) {
                if (dist < nearestAssassinDistance) {
                    nearestAssassinDistance = dist;
                    nearestAssassinThreat = other;
                }
            }
            if (dist > detectionRange) {
                continue;
            }

            visibleThreats.add(other);
            boolean hasSight = world.hasLineOfSight(
                position.x, position.y, position.z + 0.6f,
                other.getX(), other.getY(), other.getZ() + 1.0f
            );
            if (hasSight || (other instanceof Assassin && dist < MIN_ASSASSIN_DISTANCE)) {
                spotted = true;
                threatDirection.add(position).sub(other.getPosition());
            }
        }

        if (spotted) {
            stealthTimer = 3.0f;
            if (!threatDirection.isZero(0.0001f)) {
                threatDirection.nor();
            }
        } else if (stealthTimer > 0f) {
            stealthTimer = Math.max(0f, stealthTimer - delta);
            if (stealthTimer <= 0f) {
                visibleThreats.clear();
                threatDirection.setZero();
            }
        }

        isFleeing = stealthTimer > 0f;
        
        // Update spacing state with hysteresis
        boolean wasSpacing = isSpacing;
        if (nearestAssassinThreat != null) {
            if (nearestAssassinDistance < MIN_ASSASSIN_DISTANCE) {
                isSpacing = true;
            } else if (nearestAssassinDistance > MIN_ASSASSIN_DISTANCE + 4f) {
                isSpacing = false;
            }
        } else {
            isSpacing = false;
        }

        // Interrupt current move if we need to start spacing
        if (isSpacing && !wasSpacing) {
            targetPosition = null;
            moveTimer = 0f;
            logAssassinEvent("SPACING", "INTERRUPTED_MOVE", null);
        }
    }

    private Vector3 infiltrationTarget;

    public void setInfiltrationTarget(Vector3 target) {
        this.infiltrationTarget = target;
    }

    public com.castlewar.ai.assassin.AssassinState getCurrentState() {
        return aiAgent.getCurrentState();
    }

    public String getCurrentStateName() {
        com.castlewar.ai.assassin.AssassinState current = getCurrentState();
        return current != null ? current.name() : "UNKNOWN";
    }

    private void logAssassinEvent(String category, String detail, Vector3 destination) {
        AiContext ctx = aiAgent.getContext();
        if (ctx == null) {
            return;
        }
        WorldContext worldContext = ctx.getWorldContext();
        if (worldContext == null || worldContext.getDebugLog() == null) {
            return;
        }
        worldContext.getDebugLog().logAssassinEvent(this, category, detail, position, destination);
    }

    private boolean isPositionExposed(GridWorld world, Vector3 probe) {
        if (probe == null) {
            return false;
        }
        return calculateExposure(world, probe) > SAFE_EXPOSURE_THRESHOLD;
    }

    private float calculateExposure(GridWorld world, Vector3 probe) {
        if (visibleThreats.isEmpty()) {
            return 0f;
        }
        float exposure = 0f;
        for (Entity threat : visibleThreats) {
            Vector3 threatPos = threat.getPosition();
            float dist = probe.dst(threatPos);
            boolean hasLOS = world.hasLineOfSight(
                probe.x, probe.y, probe.z + 0.6f,
                threatPos.x, threatPos.y, threatPos.z + 1.0f
            );
            float weight = hasLOS ? 1.2f : 0.2f;
            exposure += weight / Math.max(dist, 1f);
        }
        return exposure;
    }

    private float getDetectionRangeFor(Unit other) {
        if (other instanceof Guard) {
            return 12f;
        }
        if (other instanceof King) {
            return 10f;
        }
        if (other instanceof Assassin) {
            return MIN_ASSASSIN_DISTANCE;
        }
        return 6f;
    }

    private boolean shouldMaintainAssassinSpacing() {
        return isSpacing && nearestAssassinThreat != null;
    }

    private boolean planAssassinSpacingMove(GridWorld world) {
        if (nearestAssassinThreat == null) {
            return false;
        }
        Vector3 threatPos = nearestAssassinThreat.getPosition();
        
        // Note: We rely on isSpacing state, so we don't check dist >= MIN_ASSASSIN_DISTANCE here anymore
        // This allows us to continue spacing until we reach the hysteresis upper bound

        // Immediate flee: pick a rapid escape direction away from the threat
        Vector3 radial = new Vector3(position).sub(threatPos);
        radial.z = 0f;
        if (radial.isZero(0.0001f)) {
            radial.set(MathUtils.randomSign(), MathUtils.randomSign(), 0f);
        }
        radial.nor();

        // Add random tangential component for unpredictability
        float tangentStrength = MathUtils.random(0.3f, 0.6f);
        Vector3 tangent = new Vector3(-radial.y, radial.x, 0f).scl(MathUtils.randomSign() * tangentStrength);
        Vector3 fleeDir = new Vector3(radial).add(tangent).nor();

        // Try immediate adjacent tiles in flee direction
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        Vector3 bestMove = null;
        float bestDist = 0f;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (Math.abs(dx) + Math.abs(dy) != 1) continue;
                Vector3 dir = new Vector3(dx, dy, 0f).nor();
                float alignment = dir.dot(fleeDir);
                if (alignment < 0.2f) continue; // Relaxed alignment check

                Vector3 candidate = getValidMoveTarget(world, currentX + dx, currentY + dy, currentZ);
                if (candidate != null) {
                    float distFromThreat = candidate.dst(threatPos);
                    // Prioritize distance, use alignment as tie-breaker
                    float score = distFromThreat + (alignment * 2.0f);
                    if (score > bestDist) {
                        bestDist = score;
                        bestMove = candidate;
                    }
                }
            }
        }

        if (bestMove != null) {
            targetPosition = bestMove;
            moveTimer = 0.2f; // Quick re-evaluation
            logAssassinEvent("SPACING", "URGENT_FLEE", targetPosition);
            return true;
        }

        // Fallback: try vertical escape
        if (canClimb) {
            for (int dz = 1; dz <= 2; dz++) {
                if (isValidMove(world, currentX, currentY, currentZ + dz)) {
                    targetPosition = new Vector3(currentX, currentY, currentZ + dz);
                    moveTimer = 0.2f;
                    logAssassinEvent("SPACING", "VERTICAL_FLEE", targetPosition);
                    return true;
                }
            }
        }

        logAssassinEvent("SPACING", "FLEE_BLOCKED", null);
        return false;
    }



    private Vector3 getGoalDirection() {
        preferredDirection.setZero();
        Vector3 goal = null;
        if (infiltrationTarget != null) {
            goal = infiltrationTarget;
        } else if (targetKing != null) {
            goal = targetKing.getPosition();
        }

        if (goal != null) {
            preferredDirection.set(goal).sub(position);
        } else if (!threatDirection.isZero(0.0001f)) {
            preferredDirection.set(threatDirection);
        }

        if (!preferredDirection.isZero(0.0001f)) {
            preferredDirection.z = 0f;
            if (!preferredDirection.isZero(0.0001f)) {
                preferredDirection.nor();
            }
        }
        return preferredDirection;
    }

    

    private boolean attemptCastleInfiltration(GridWorld world, Vector3 target) {
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        // First: try to find a door or gate
        Vector3 doorEntry = scanForDoorEntry(world, currentX, currentY, currentZ, 8);
        if (doorEntry != null) {
            targetPosition = doorEntry;
            logAssassinEvent("INFILTRATE", "DOOR_APPROACH", targetPosition);
            return true;
        }

        // Second: scan perimeter for climbable wall
        Vector3 climbPoint = scanPerimeterForClimb(world, target);
        if (climbPoint != null) {
            targetPosition = climbPoint;
            logAssassinEvent("INFILTRATE", "CLIMB_POINT", targetPosition);
            return true;
        }

        logAssassinEvent("INFILTRATE", "NO_ENTRY_FOUND", null);
        return false;
    }

    private Vector3 scanForDoorEntry(GridWorld world, int cx, int cy, int cz, int radius) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    
                    GridWorld.BlockState block = world.getBlock(x, y, cz);
                    if (block == GridWorld.BlockState.DOOR) {
                        // Check if door is accessible
                        Vector3 approach = getValidMoveTarget(world, x, y, cz);
                        if (approach != null) {
                            return approach;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Vector3 scanPerimeterForClimb(GridWorld world, Vector3 target) {
        if (!canClimb) return null;

        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);
        
        // Find nearest castle wall
        Vector3 bestClimbPoint = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        
        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                    
                    int x = currentX + dx;
                    int y = currentY + dy;
                    
                    // Check if this is an exterior castle wall
                    GridWorld.BlockState block = world.getBlock(x, y, currentZ);
                    if (!isCastleBlock(block)) continue;
                    
                    // Check if we can climb here
                    if (!isAdjacentToWall(world, x, y, currentZ)) continue;
                    
                    // Try to find valid climb path
                    for (int dz = 1; dz <= 4; dz++) {
                        if (isValidMove(world, x, y, currentZ + dz)) {
                            Vector3 climbTop = new Vector3(x, y, currentZ + dz);
                            float distToTarget = climbTop.dst(target);
                            float score = -distToTarget + dz * 2f; // Prefer higher climbs closer to target
                            
                            if (score > bestScore) {
                                bestScore = score;
                                // Set climb point at base of wall
                                bestClimbPoint = getValidMoveTarget(world, x, y, currentZ);
                                if (bestClimbPoint == null) {
                                    bestClimbPoint = new Vector3(x, y, currentZ);
                                }
                            }
                            break;
                        }
                    }
                }
            }
            if (bestClimbPoint != null) break;
        }
        
        return bestClimbPoint;
    }

    private boolean isCastleBlock(GridWorld.BlockState block) {
        return block == GridWorld.BlockState.CASTLE_WHITE ||
               block == GridWorld.BlockState.CASTLE_BLACK ||
               block == GridWorld.BlockState.CASTLE_WHITE_FLOOR ||
               block == GridWorld.BlockState.CASTLE_BLACK_FLOOR ||
               block == GridWorld.BlockState.CASTLE_WHITE_STAIR ||
               block == GridWorld.BlockState.CASTLE_BLACK_STAIR;
    }

    public boolean canBackstab(Unit target, List<Entity> entities) {
        if (target == null || target.isDead() || target.getTeam() == this.team) {
            return false;
        }
        
        // Must be close
        float dist = position.dst(target.getPosition());
        if (dist > attackRange * 1.5f) {
            return false;
        }
        
        // Target must be isolated (no allies within 8 units)
        for (Entity e : entities) {
            if (e == target || !(e instanceof Unit) || e == this) continue;
            Unit other = (Unit) e;
            if (other.getTeam() == target.getTeam() && !other.isDead()) {
                if (target.getPosition().dst(other.getPosition()) < 8f) {
                    return false; // Target has backup
                }
            }
        }
        
        return true;
    }

    public void executeBackstab(Unit target) {
        if (!canBackstab(target, aiAgent.getContext().getEntities())) {
            return;
        }
        
        // Instant kill
        target.takeDamage(target.getHp() + 1f);
        logAssassinEvent("COMBAT", "BACKSTAB_KILL", target.getPosition());
        
        // Trigger alert for nearby enemies
        triggerAwarenessCue(AwarenessIcon.ALERT, false);
    }

    private boolean attemptDesperateEscape(GridWorld world) {
        // 1. Try to climb higher (parkour!)
        if (canClimb) {
             for (int dz = 1; dz <= 3; dz++) {
                 // Check immediate vertical
                 if (isValidMove(world, Math.round(position.x), Math.round(position.y), Math.round(position.z) + dz)) {
                     targetPosition = new Vector3(Math.round(position.x), Math.round(position.y), Math.round(position.z) + dz);
                     moveTimer = 0.2f;
                     logAssassinEvent("SPACING", "DESPERATE_CLIMB", targetPosition);
                     return true;
                 }
             }
        }

        // 2. Wall Scramble: Try ANY valid adjacent move that isn't directly towards threat
        Vector3 threatPos = nearestAssassinThreat != null ? nearestAssassinThreat.getPosition() : null;
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                
                Vector3 candidate = getValidMoveTarget(world, currentX + dx, currentY + dy, currentZ);
                if (candidate != null) {
                    // If we have a threat, avoid moving directly into it
                    if (threatPos != null && candidate.dst(threatPos) < position.dst(threatPos) - 0.5f) {
                        continue; // Don't get closer
                    }
                    targetPosition = candidate;
                    moveTimer = 0.15f;
                    logAssassinEvent("SPACING", "DESPERATE_SCRAMBLE", targetPosition);
                    return true;
                }
            }
        }

        // 3. Fight: If we can't run, turn and fight
        if (threatPos != null && position.dst(threatPos) < 2.0f) {
             // Force state change to STRIKE? 
             // Or just stop fleeing so we can attack
             isFleeing = false;
             isSpacing = false; // Stop trying to space
             logAssassinEvent("SPACING", "CORNERED_FIGHT", threatPos);
             return true; // Handled by state change next frame
        }

        return false;
    }
}

