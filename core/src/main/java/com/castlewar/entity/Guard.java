package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.guard.GuardAgent;
import com.castlewar.ai.guard.GuardState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.world.GridWorld;

public class Guard extends Unit {
    public enum GuardType {
        ENTOURAGE,
        PATROL
    }

    private static final String[] NAMES = {"Guard", "Sentry", "Warden", "Protector", "Shield", "Knight"};
    private static final float ALERT_MEMORY_DURATION = 5f;

    private final GuardType type;
    private final GuardAgent aiAgent;
    private Entity targetToFollow; // For Entourage
    private float moveTimer = 0f;
    private Vector3 targetPosition = null;
    private final Vector3 facing = new Vector3(1, 0, 0);
    private final Vector3 lastKnownEnemyPosition = new Vector3();
    private boolean hasLastKnownEnemy;
    private float alertMemoryTimer;
    private final Vector3 tempVector = new Vector3();
    private transient float aiDeltaSnapshot;

    public Guard(float x, float y, float z, Team team, GuardType type, WorldContext worldContext) {
        super(x, y, z, team, generateName(type), 80f, 50f);
        this.type = type;
        this.aiAgent = new GuardAgent(this, new AiContext(worldContext));
    }

    private static String generateName(GuardType type) {
        return (type == GuardType.ENTOURAGE ? "Royal " : "Castle ") + 
               NAMES[MathUtils.random(NAMES.length - 1)] + " " + MathUtils.random(100, 999);
    }

    public void setTargetToFollow(Entity target) {
        this.targetToFollow = target;
    }

    public GuardType getType() {
        return type;
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
    }

    public void updateFacing() {
        if (velocity.len2() > 0.1f) {
            facing.set(velocity).nor();
            facing.z = 0; 
            facing.nor();
        }
    }

    public void updateMovement(float delta) {
        if (isStunned()) {
            velocity.x = 0;
            velocity.y = 0;
            return;
        }
        
        if (targetPosition != null) {
            float speed = 2.5f;
            Vector3 direction = new Vector3(targetPosition).sub(position);
            direction.z = 0;
            direction.nor();
            
            velocity.x = direction.x * speed;
            velocity.y = direction.y * speed;
            
            float dst2 = Vector3.dst2(position.x, position.y, 0, targetPosition.x, targetPosition.y, 0);
            if (dst2 < 0.1f * 0.1f) {
                velocity.x = 0;
                velocity.y = 0;
                targetPosition = null;
                moveTimer = MathUtils.random(0.5f, 2f);
            }
        } else {
            velocity.x = 0;
            velocity.y = 0;
            moveTimer -= delta;
        }
    }

    public boolean isMoveTimerReady() {
        return moveTimer <= 0;
    }

    public void updateAttack() {
        if (!isStunned() && targetEnemy != null && !targetEnemy.isDead()) {
            rememberEnemySighting(targetEnemy.getPosition());
            float dist = position.dst(targetEnemy.getPosition());
            if (dist < attackRange) {
                attack(targetEnemy);
                velocity.x = 0;
                velocity.y = 0;
                targetPosition = null;
            } else if (dist < 6f) {
                targetPosition = targetEnemy.getPosition();
            }
        }
    }

    public void decideNextMove(GridWorld world) {
        if (type == GuardType.ENTOURAGE && targetToFollow != null) {
            followTarget(world);
        } else {
            patrol(world);
        }
    }

    public void followTarget(GridWorld world) {
        // If far, move closer. If close, wander nearby.
        float dist = position.dst(targetToFollow.getPosition());
        if (dist > 3f) {
            // Move towards target
            // Simple pathfinding: move to adjacent block closest to target
            // For now, just pick a random valid move that reduces distance
            pickMoveTowards(world, targetToFollow.getPosition());
        } else {
            // Wander nearby
            patrol(world);
        }
    }

    private void pickMoveTowards(GridWorld world, Vector3 target) {
        pickSmartMove(world, target);
    }

    public void patrol(GridWorld world) {
        // Similar to King's wander logic
        int currentX = Math.round(position.x);
        int currentY = Math.round(position.y);
        int currentZ = Math.round(position.z);

        int dx = MathUtils.random(-1, 1);
        int dy = MathUtils.random(-1, 1);
        if (dx != 0 && dy != 0) dy = 0;
        if (dx == 0 && dy == 0) return;

        int newX = currentX + dx;
        int newY = currentY + dy;
        int newZ = currentZ;

        Vector3 move = getValidMoveTarget(world, newX, newY, newZ);
        if (move != null) {
            targetPosition = move;
        } else {
             GridWorld.BlockState currentBlock = world.getBlock(currentX, currentY, currentZ);
            if (isStair(currentBlock)) {
                int dz = MathUtils.randomSign();
                if (isValidMove(world, currentX, currentY, currentZ + dz)) {
                    targetPosition = new Vector3(currentX, currentY, currentZ + dz);
                }
            }
        }
    }

    @Override
    public void scanForEnemies(java.util.List<Entity> entities, GridWorld world) {
        float closestDist = 10f; // Vision range
        Unit closest = null;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e.getTeam() != this.team && !((Unit)e).isDead()) {
                float d = position.dst(e.getPosition());
                if (d < closestDist) {
                    // Check FOV
                    Vector3 toEnemy = new Vector3(e.getPosition()).sub(position);
                    toEnemy.z = 0; 
                    toEnemy.nor();
                    
                    float dot = facing.dot(toEnemy);
                    // 120 degree FOV
                    if (dot > 0.5f) {
                        // Stealth check for Assassins
                        if (e instanceof Assassin) {
                            if (d > 6f) continue; // Reduced range
                            if (dot < 0.8f) continue; // Reduced FOV (must be more in front)
                        }

                        // Check LOS
                        if (world.hasLineOfSight(position.x, position.y, position.z + 1.5f, 
                                               e.getX(), e.getY(), e.getZ() + 1.0f)) {
                             closestDist = d;
                             closest = (Unit)e;
                        }
                    } else if (d < 2.0f) {
                        // Proximity sense
                        closestDist = d;
                        closest = (Unit)e;
                    }
                }
            }
        }
        setTargetEnemy(closest);
        if (closest != null) {
            rememberEnemySighting(closest.getPosition());
        }
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 7.5f;
    }

    public float getAttackRange() {
        return attackRange;
    }

    public void setTargetPosition(Vector3 target) {
        this.targetPosition = target;
    }

    public Vector3 getTargetPosition() {
        return targetPosition;
    }

    public void clearTargetPosition() {
        this.targetPosition = null;
    }

    public void stopMoving() {
        velocity.x = 0;
        velocity.y = 0;
        targetPosition = null;
    }

    public GuardAgent getAiAgent() {
        return aiAgent;
    }

    public float getAiDeltaSnapshot() {
        return aiDeltaSnapshot;
    }

    public void rememberEnemySighting(Vector3 enemyPosition) {
        if (enemyPosition == null) {
            return;
        }
        lastKnownEnemyPosition.set(enemyPosition);
        hasLastKnownEnemy = true;
        alertMemoryTimer = ALERT_MEMORY_DURATION;
    }

    public void reportNoise(Vector3 location) {
        rememberEnemySighting(location);
    }

    public void decayAlertMemory(float delta) {
        if (!hasLastKnownEnemy) {
            return;
        }
        alertMemoryTimer = Math.max(0f, alertMemoryTimer - delta);
        if (alertMemoryTimer == 0f) {
            hasLastKnownEnemy = false;
        }
    }

    public boolean hasInvestigationTarget() {
        return hasLastKnownEnemy;
    }

    public void moveTowardLastSighting(GridWorld world) {
        if (!hasLastKnownEnemy) {
            return;
        }
        if (position.dst2(lastKnownEnemyPosition) < 1f) {
            hasLastKnownEnemy = false;
            return;
        }
        if (targetPosition == null) {
            pickMoveTowards(world, lastKnownEnemyPosition);
        }
    }

    public void retreatFromThreat(GridWorld world) {
        Vector3 threatPosition = null;
        if (targetEnemy != null && !targetEnemy.isDead()) {
            threatPosition = targetEnemy.getPosition();
        } else if (hasLastKnownEnemy) {
            threatPosition = lastKnownEnemyPosition;
        }

        tempVector.set(position);
        if (threatPosition != null) {
            tempVector.sub(threatPosition).nor().scl(6f).add(position);
        } else {
            tempVector.add(MathUtils.random(-4f, 4f), MathUtils.random(-4f, 4f), 0f);
        }

        pickMoveTowards(world, tempVector);
    }

    public boolean needsToFlee() {
        return hp < maxHp * 0.3f;
    }

    public void changeState(GuardState nextState) {
        aiAgent.changeState(nextState);
    }
}
