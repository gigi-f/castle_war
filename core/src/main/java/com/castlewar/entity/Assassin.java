package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;
import java.util.List;

public class Assassin extends Unit {
    private static final String[] NAMES = {"Shadow", "Ghost", "Viper", "Phantom", "Wraith", "Blade"};
    
    private Entity targetKing;
    private float moveTimer = 0f;
    // targetPosition is inherited from Unit - don't redeclare it!
    private boolean isFleeing = false;

    public Assassin(float x, float y, float z, Team team) {
        super(x, y, z, team, generateName(), 30f, 40f);
        this.canClimb = true;
    }

    private static String generateName() {
        return NAMES[MathUtils.random(NAMES.length - 1)];
    }

    public void setTargetKing(Entity king) {
        this.targetKing = king;
    }

    private Vector3 lastPosition = new Vector3();
    private float stuckTimer = 0f;

    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (hp <= 0) return;

        // Assassins are very fast - 2x speed
        float speed = 8.0f; 
        
        if (targetPosition != null) {
            Vector3 direction = new Vector3(targetPosition).sub(position);
            
            direction.nor();
            
            velocity.x = direction.x * speed;
            velocity.y = direction.y * speed;
            
            // If climbing (target Z > current Z), apply Z velocity
            if (targetPosition.z > position.z + 0.1f) {
                 velocity.z = direction.z * speed;
            }
            
            float dst2 = position.dst2(targetPosition);
            
            if (dst2 < 0.1f * 0.1f) {
                velocity.set(0,0,0);
                targetPosition = null;
                moveTimer = 0.2f; 
            }
        } else {
            velocity.set(0,0,0);
            moveTimer -= delta;
            if (moveTimer <= 0) {
                decideNextMove(world);
            }
        }
        
        super.applyPhysics(delta, world);
        
        // Stuck detection - Disabled for now as it might interfere with A*
        /*
        if (position.dst(lastPosition) < 0.1f * delta) {
            stuckTimer += delta;
        } else {
            stuckTimer = 0f;
            lastPosition.set(position);
        }
        
        if (stuckTimer > 1.0f) {
            // Been stuck for 1 second, force random move
            pickRandomMove(world);
            stuckTimer = 0f;
        }
        */

        // Attack logic
        if (attackTimer > 0) {
            attackTimer -= delta;
        }

        // Check for kill (King)
        if (targetKing != null && targetKing instanceof Unit && !((Unit)targetKing).isDead() && position.dst(targetKing.getPosition()) < attackRange) {
             attack((Unit)targetKing);
        }
        
        // Check for nearby guards to attack if they are close
        if (attackTimer <= 0 && targetEnemy != null && !targetEnemy.isDead()) {
             if (position.dst(targetEnemy.getPosition()) < attackRange) {
                 attack(targetEnemy);
             }
        }
    }
    
    public void checkForGuards(List<Entity> entities, GridWorld world) {
        // If close to King, ignore everything (Kamikaze)
        if (targetKing != null && position.dst(targetKing.getPosition()) < 10f) {
            isFleeing = false;
            return;
        }

        isFleeing = false;
        
        // Check for other Assassins - they recognize each other as threats
        for (Entity e : entities) {
            if (e instanceof Assassin && e != this) {
                float dist = position.dst(e.getPosition());
                if (dist < 6f) { // Detection range for other assassins
                    if (world.hasLineOfSight(position.x, position.y, position.z + 0.5f, 
                                           e.getX(), e.getY(), e.getZ() + 0.5f)) {
                        isFleeing = true;
                        break;
                    }
                }
            }
        }
        
        // Original guard detection logic commented out
        /*
        for (Entity e : entities) {
            if (e instanceof Guard && e.getTeam() != this.team) {
                float dist = position.dst(e.getPosition());
                if (dist < 8f) { // Guard detection range approx
                    if (world.hasLineOfSight(position.x, position.y, position.z + 0.5f, 
                                           e.getX(), e.getY(), e.getZ() + 0.5f)) {
                        isFleeing = true;
                        break;
                    }
                }
            }
        }
        */
    }

    private Vector3 infiltrationTarget;

    public void setInfiltrationTarget(Vector3 target) {
        this.infiltrationTarget = target;
    }

    private void decideNextMove(GridWorld world) {
        if (isFleeing) {
            pickRandomMove(world);
        } else {
            // Priority: Infiltration -> King
            Vector3 target = null;
            
            if (infiltrationTarget != null) {
                // Check if we reached infiltration target (or close to it)
                if (position.dst(infiltrationTarget) < 5.0f) {
                    infiltrationTarget = null; // Reached, now hunt King
                } else {
                    target = infiltrationTarget;
                }
            }
            
            if (target == null && targetKing != null) {
                target = targetKing.getPosition();
            }
            
            if (target != null) {
                pickSmartMove(world, target);
            } else {
                pickRandomMove(world);
            }
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

}

