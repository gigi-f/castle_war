package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.guard.GuardAgent;
import com.castlewar.world.GridWorld;

public class Guard extends Unit {
    public enum GuardType {
        ENTOURAGE,
        PATROL
    }

    private static final String[] NAMES = {"Guard", "Sentry", "Warden", "Protector", "Shield", "Knight"};

    private final GuardType type;
    private final GuardAgent aiAgent;
    private Entity targetToFollow; // For Entourage
    private float moveTimer = 0f;
    private Vector3 targetPosition = null;
    private Vector3 facing = new Vector3(1, 0, 0);
    private transient GridWorld aiWorldSnapshot;
    private transient float aiDeltaSnapshot;

    public Guard(float x, float y, float z, Team team, GuardType type) {
        super(x, y, z, team, generateName(type), 80f, 50f);
        this.type = type;
        this.aiAgent = new GuardAgent(this, new AiContext());
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

        this.aiWorldSnapshot = world;
        this.aiDeltaSnapshot = delta;
        aiAgent.update(delta);
    }

    public void runLegacyBehavior() {
        if (aiWorldSnapshot == null) {
            return;
        }
        legacyUpdate(aiDeltaSnapshot, aiWorldSnapshot);
    }

    private void legacyUpdate(float delta, GridWorld world) {
        if (velocity.len2() > 0.1f) {
            facing.set(velocity).nor();
            facing.z = 0; 
            facing.nor();
        }

        if (isStunned()) {
            // Freeze during stun
            velocity.x = 0;
            velocity.y = 0;
        } else if (targetPosition != null) {
            // Move towards target
            float speed = 2.5f; // Slightly faster than King
            Vector3 direction = new Vector3(targetPosition).sub(position);
            direction.z = 0; // Ignore Z for horizontal movement
            direction.nor();
            
            velocity.x = direction.x * speed;
            velocity.y = direction.y * speed;
            
            // Check if close enough (ignoring Z for now, or check 3D?)
            // If we are close in X/Y, we might be at the step.
            float dst2 = Vector3.dst2(position.x, position.y, 0, targetPosition.x, targetPosition.y, 0);
            
            if (dst2 < 0.1f * 0.1f) { // Very close
                // Snap to center of block? Or just clear target?
                // If we snap, we might teleport.
                // Let's just clear target and stop.
                velocity.x = 0;
                velocity.y = 0;
                targetPosition = null;
                moveTimer = MathUtils.random(0.5f, 2f);
            }
        } else if (!isStunned()) {
            velocity.x = 0;
            velocity.y = 0;
            moveTimer -= delta;
            if (moveTimer <= 0) {
                decideNextMove(world);
            }
        }
        
        if (!isStunned() && targetEnemy != null && !targetEnemy.isDead()) {
            float dist = position.dst(targetEnemy.getPosition());
            if (dist < attackRange) {
                attack(targetEnemy);
                // Stop moving to fight
                velocity.x = 0;
                velocity.y = 0;
                targetPosition = null;
            } else if (dist < 6f) {
                // Aggro: Move towards enemy
                targetPosition = targetEnemy.getPosition();
            }
        }
        
        super.applyPhysics(delta, world);
    }

    private void decideNextMove(GridWorld world) {
        if (type == GuardType.ENTOURAGE && targetToFollow != null) {
            followTarget(world);
        } else {
            patrol(world);
        }
    }

    private void followTarget(GridWorld world) {
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

    private void patrol(GridWorld world) {
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
        targetEnemy = closest;
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 7.5f;
    }
}
