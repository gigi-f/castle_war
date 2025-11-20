package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.assassin.AssassinAgent;
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
    private Unit nearestAssassinThreat;
    private float nearestAssassinDistance = Float.MAX_VALUE;
    private final AssassinAgent aiAgent;
    private transient float aiDeltaSnapshot;

    public Assassin(float x, float y, float z, Team team, WorldContext worldContext) {
        super(x, y, z, team, generateName(), 30f, 40f);
        this.canClimb = true;
        this.aiAgent = new AssassinAgent(this, new AiContext(worldContext));
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
        aiAgent.update(delta);

        super.applyPhysics(delta, world);
        resolvePostPhysicsAttacks();
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 5.0f;
    }

    public void performSneakBehavior(float delta, GridWorld world) {
        if (targetPosition == null && moveTimer <= 0f) {
            decideNextMove(world);
        }
        resolveMovement(delta, world);
    }

    public void performStrikeBehavior(float delta, GridWorld world) {
        if (targetEnemy != null && !targetEnemy.isDead()) {
            if (targetPosition == null) {
                pickSmartMove(world, targetEnemy.getPosition());
            }
        } else if (targetKing != null && targetKing instanceof Unit && !((Unit) targetKing).isDead()) {
            if (targetPosition == null) {
                pickSmartMove(world, targetKing.getPosition());
            }
        }
        resolveMovement(delta, world);
    }

    public void performEscapeBehavior(float delta, GridWorld world) {
        if (targetPosition == null || moveTimer <= 0f) {
            pickShadowRetreat(world);
        }
        resolveMovement(delta, world);
    }

    private void resolveMovement(float delta, GridWorld world) {
        if (isStunned()) {
            velocity.x = 0f;
            velocity.y = 0f;
            return;
        }

        if (targetPosition != null) {
            Vector3 direction = tmp.set(targetPosition).sub(position).nor();
            velocity.x = direction.x * BASE_SPEED;
            velocity.y = direction.y * BASE_SPEED;

            if (targetPosition.z > position.z + 0.1f) {
                velocity.z = direction.z * BASE_SPEED;
                velocity.z += gravity * delta;
            }

            float dst2 = position.dst2(targetPosition);
            if (dst2 < 0.2f * 0.2f) {
                if (currentPath != null && !currentPath.isEmpty()) {
                    targetPosition = currentPath.remove(0);
                } else {
                    velocity.set(0f, 0f, 0f);
                    targetPosition = null;
                    moveTimer = 0f;
                }
            }
        } else {
            velocity.set(0f, 0f, 0f);
            moveTimer -= delta;
            if (moveTimer <= 0f) {
                decideNextMove(world);
            }
        }
    }

    private void resolvePostPhysicsAttacks() {
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

    public AssassinAgent getAiAgent() {
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
    }

    private Vector3 infiltrationTarget;

    public void setInfiltrationTarget(Vector3 target) {
        this.infiltrationTarget = target;
    }

    private void decideNextMove(GridWorld world) {
        if (shouldMaintainAssassinSpacing() && planAssassinSpacingMove(world)) {
            return;
        }

        if (isFleeing) {
            pickShadowRetreat(world);
            return;
        }

        Vector3 target = null;

        if (infiltrationTarget != null) {
            if (position.dst(infiltrationTarget) < 5.0f) {
                infiltrationTarget = null;
            } else {
                target = infiltrationTarget;
            }
        }

        if (target == null && targetKing != null) {
            target = targetKing.getPosition();
        }

        if (target != null) {
            pickSmartMove(world, target);
            if (targetPosition != null && isPositionExposed(world, targetPosition)) {
                pickShadowRetreat(world);
            }
        } else {
            pickRandomMove(world);
        }
    }

    private void pickRandomMove(GridWorld world) {
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        // Try a few times to find a valid random move
        for (int i = 0; i < 5; i++) {
            int dx = MathUtils.random(-1, 1);
            int dy = MathUtils.random(-1, 1);
            int dz = MathUtils.random(-1, 1);
            
            // Don't move diagonally in 3D
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
            
            int nx = currentX + dx;
            int ny = currentY + dy;
            int nz = currentZ + dz;
            
            Vector3 move = null;
            if (dz != 0) { // Vertical move
                 if (isValidMove(world, nx, ny, nz)) move = new Vector3(nx, ny, nz);
            } else { // Horizontal move
                 move = getValidMoveTarget(world, nx, ny, nz);
            }
            
            if (move != null) {
                targetPosition = move;
                return;
            }
        }
        // If no valid random move found after several tries, stay put for now
        targetPosition = null;
    }

    private void pickShadowRetreat(GridWorld world) {
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        Vector3 bestMove = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        Vector3 preferredDir = new Vector3(getGoalDirection());

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (Math.abs(dx) + Math.abs(dy) != 1) continue;
                int nx = currentX + dx;
                int ny = currentY + dy;
                Vector3 candidate = getValidMoveTarget(world, nx, ny, currentZ);
                if (candidate == null) continue;
                float score = evaluateStealthMove(world, candidate, preferredDir);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = candidate;
                }
            }
        }

        if (bestMove == null) {
            for (int dz = -1; dz <= 1; dz += 2) {
                int nz = currentZ + dz;
                if (!isValidMove(world, currentX, currentY, nz)) continue;
                Vector3 candidate = new Vector3(currentX, currentY, nz);
                float score = evaluateStealthMove(world, candidate, preferredDir);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = candidate;
                }
            }
        }

        if (bestMove != null) {
            targetPosition = bestMove;
        } else {
            pickRandomMove(world);
        }
    }

    private float evaluateStealthMove(GridWorld world, Vector3 candidate, Vector3 preferredDir) {
        float exposure = calculateExposure(world, candidate);
        float distanceScore = 0f;
        for (Entity threat : visibleThreats) {
            float before = position.dst2(threat.getPosition());
            float after = candidate.dst2(threat.getPosition());
            distanceScore += (after - before);
        }

        float directionScore = 0f;
        Vector3 dir = new Vector3(candidate).sub(position);
        dir.z = 0f;
        if (!dir.isZero(0.0001f)) {
            dir.nor();
            if (preferredDir != null && !preferredDir.isZero(0.0001f)) {
                directionScore = dir.dot(preferredDir);
            } else if (!threatDirection.isZero(0.0001f)) {
                directionScore = dir.dot(threatDirection);
            }
        }

        float spacingPenalty = 0f;
        if (nearestAssassinThreat != null) {
            float distAfter = candidate.dst(nearestAssassinThreat.getPosition());
            if (distAfter < MIN_ASSASSIN_DISTANCE) {
                spacingPenalty = (MIN_ASSASSIN_DISTANCE - distAfter) * 3f;
            }
        }

        return distanceScore * 0.05f + directionScore * 3.0f - exposure * 6f - spacingPenalty;
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
        return nearestAssassinThreat != null && nearestAssassinDistance < MIN_ASSASSIN_DISTANCE;
    }

    private boolean planAssassinSpacingMove(GridWorld world) {
        if (nearestAssassinThreat == null) {
            return false;
        }
        Vector3 threatPos = nearestAssassinThreat.getPosition();
        float dist = position.dst(threatPos);
        if (dist >= MIN_ASSASSIN_DISTANCE) {
            return false;
        }

        Vector3 radial = new Vector3(position).sub(threatPos);
        radial.z = 0f;
        if (radial.isZero(0.0001f)) {
            radial.set(1f, 0f, 0f);
        }
        radial.nor();

        Vector3 tangentA = new Vector3(-radial.y, radial.x, 0f).nor();
        Vector3 tangentB = new Vector3(radial.y, -radial.x, 0f).nor();
        Vector3 goalDir = new Vector3(getGoalDirection());
        Vector3 preferred = tangentA;
        if (!goalDir.isZero(0.0001f)) {
            float dotA = tangentA.dot(goalDir);
            float dotB = tangentB.dot(goalDir);
            preferred = dotB > dotA ? tangentB : tangentA;
        } else if (MathUtils.randomBoolean()) {
            preferred = tangentB;
        }

        Vector3 desired = new Vector3(threatPos)
            .add(new Vector3(radial).scl(MIN_ASSASSIN_DISTANCE + 1f))
            .add(new Vector3(preferred).scl(4f));

        Vector3 safe = findClosestValid(world, desired);
        if (safe != null) {
            targetPosition = safe;
            return true;
        }
        return false;
    }

    private Vector3 findClosestValid(GridWorld world, Vector3 desired) {
        int baseZ = Math.round(desired.z);
        int baseX = Math.round(desired.x);
        int baseY = Math.round(desired.y);
        Vector3 best = null;
        float bestDist2 = Float.MAX_VALUE;

        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                        continue;
                    }
                    int x = baseX + dx;
                    int y = baseY + dy;
                    Vector3 move = getValidMoveTarget(world, x, y, baseZ);
                    if (move == null) continue;
                    float d2 = move.dst2(desired);
                    if (d2 < bestDist2) {
                        bestDist2 = d2;
                        best = move;
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        return best;
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

}

