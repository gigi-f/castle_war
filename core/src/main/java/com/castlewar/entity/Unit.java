package com.castlewar.entity;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;
import com.castlewar.world.GridWorld;
import java.util.ArrayList;
import java.util.List;

public abstract class Unit extends Entity {
    private static final float DAMAGE_FLASH_DURATION = 0.5f;
    private static final float HIT_STUN_DURATION = 0.5f;
    private static final float CORPSE_LIFETIME = 30f;
    private static final float KNOCKBACK_DAMPING = 4f;
    private static final float BASE_KNOCKBACK_STRENGTH = 25f;
    private static final float MIN_VERTICAL_RECOIL = 2.5f;
    private static final float AWARENESS_ICON_DURATION = 0.85f;
    private static final float AWARENESS_FREEZE_DURATION = 0.45f;
    private static final float CURIOUS_STATE_DURATION = 5f;
    private static final float SURPRISED_STATE_DURATION = 5f;
    private static final float AWARENESS_REARM_DURATION = 60f;

    public enum AwarenessIcon {
        NONE(""),
        ALERT("!"),
        INVESTIGATE("?");

        private final String symbol;

        AwarenessIcon(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public enum AwarenessState {
        RELAXED,
        CURIOUS,
        SURPRISED
    }

    protected String name;
    protected float hp;
    protected float maxHp;
    protected float stamina;
    protected float maxStamina;
    protected final Vector3 facing = new Vector3(1, 0, 0);

    public Unit(float x, float y, float z, Team team, String name, float maxHp, float maxStamina) {
        super(x, y, z, team);
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.maxStamina = maxStamina;
        this.stamina = maxStamina;
    }

    public String getName() { return name; }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp; }
    public Vector3 getFacing() { return facing; }

    protected boolean canClimb = false;

    // Simple movement/targeting fields (pathfinding removed)
    protected Vector3 targetPosition = null;

    public Vector3 getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(Vector3 target) {
        this.targetPosition = target;
    }

    public void clearTargetPosition(String reason) {
        this.targetPosition = null;
    }

    protected boolean isStair(GridWorld.BlockState block) {
        return block == GridWorld.BlockState.CASTLE_WHITE_STAIR ||
               block == GridWorld.BlockState.CASTLE_BLACK_STAIR;
    }
    
    protected boolean isFloor(GridWorld.BlockState block) {
         return block == GridWorld.BlockState.CASTLE_WHITE_FLOOR ||
                block == GridWorld.BlockState.CASTLE_BLACK_FLOOR;
    }

    public boolean isValidMove(GridWorld world, int x, int y, int z) {
        // Bounds check
        if (x < 0 || x >= world.getWidth() || y < 0 || y >= world.getDepth() || z < 0 || z >= world.getHeight()) {
            return false;
        }

        GridWorld.BlockState targetBlock = world.getBlock(x, y, z);
        // Check if target is passable
        if (world.isOpaque(targetBlock)) {
            return false;
        }

        // Check footing
        GridWorld.BlockState belowBlock = world.getBlock(x, y, z - 1);
        boolean solidFooting = world.isSolid(belowBlock) || isStair(targetBlock);
        
        // Climbing logic
        if (canClimb && isAdjacentToWall(world, x, y, z)) {
            return true; // Climbing ignores footing
        }

        return solidFooting;
    }

    // Helper to check if a move to (x,y,z) is valid, possibly involving a hop to z+1
    public Vector3 getValidMoveTarget(GridWorld world, int x, int y, int z) {
        // 1. Check direct move
        if (isValidMove(world, x, y, z)) {
            return new Vector3(x, y, z);
        }
        
        // 2. Check hop (move to x,y,z+1)
        GridWorld.BlockState targetBlock = world.getBlock(x, y, z);
        if (world.isSolid(targetBlock)) {
            if (z + 1 < world.getHeight()) {
                if (isValidMove(world, x, y, z + 1)) {
                    return new Vector3(x, y, z + 1);
                }
            }
        }
        
        return null;
    }

    protected boolean isAdjacentToWall(GridWorld world, int x, int y, int z) {
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] off : offsets) {
            GridWorld.BlockState b = world.getBlock(x + off[0], y + off[1], z);
            if (world.isOpaque(b)) return true;
        }
        return false;
    }

    /**
     * Lightweight replacement for previous pathfinding: pick a direct navigable
     * tile near the target and set it as `targetPosition`. Clears any stored path.
     */
    public void pickSmartMove(GridWorld world, Vector3 target) {
        // no path storage any more — pick a single direct target
        if (target == null) {
            this.targetPosition = null;
            return;
        }
        int tx = Math.round(target.x);
        int ty = Math.round(target.y);
        int tz = Math.round(target.z);

        Vector3 candidate = getValidMoveTarget(world, tx, ty, tz);
        if (candidate == null) {
            // try at our current Z
            candidate = getValidMoveTarget(world, tx, ty, Math.round(position.z));
        }

        if (candidate != null) {
            this.targetPosition = candidate;
        } else {
            this.targetPosition = null;
        }
    }

    // Combat stats
    protected float attackTimer = 0f;
    protected float attackDamage = 10f;
    protected float attackRange = 1.5f;
    protected float attackCooldown = 1.0f;
    
    /**
     * Returns the attack range of this unit.
     */
    public float getAttackRange() {
        return attackRange;
    }
    
    /**
     * Checks if this unit can currently attack.
     * 
     * @return true if attack cooldown is ready and unit is not stunned/dead
     */
    public boolean canAttack() {
        return attackTimer <= 0 && !isDead() && !isStunned();
    }

    protected float damageFlashTimer = 0f;
    protected float hitStunTimer = 0f;
    protected float corpseTimer = 0f;
    private boolean deathRegistered = false;
    private AwarenessIcon awarenessIcon = AwarenessIcon.NONE;
    private float awarenessIconTimer = 0f;
    private float awarenessFreezeTimer = 0f;
    private AwarenessState awarenessState = AwarenessState.RELAXED;
    private float awarenessStateTimer = 0f;
    private float awarenessCooldownTimer = 0f;
    private AwarenessState awarenessLockState = AwarenessState.RELAXED;

    private final Vector3 knockbackImpulse = new Vector3();
    private final Vector3 knockbackPlanarDir = new Vector3();
    private final Vector3 lastHitDirection = new Vector3(0, 1, 0);
    private final Vector3 corpseForward = new Vector3(0, 1, 0);

    public void takeDamage(float amount) {
        takeHit(amount, null);
    }

    public void takeHit(float amount, Unit attacker) {
        if (amount <= 0) {
            return;
        }

        hp -= amount;
        if (hp < 0) {
            hp = 0;
        }

        damageFlashTimer = DAMAGE_FLASH_DURATION;
        hitStunTimer = HIT_STUN_DURATION;

        Vector3 hitDir = tmp.setZero();
        if (attacker != null) {
            hitDir.set(position).sub(attacker.getPosition()).nor();
        }

        if (hitDir.isZero(0.0001f)) {
            hitDir.set(MathUtils.randomSign(), 0, 0);
        }

        float knockbackStrength = attacker != null
            ? attacker.getKnockbackStrengthAgainst(this)
            : getBaseKnockbackStrength();
        applyKnockback(hitDir, knockbackStrength);
        lastHitDirection.set(hitDir);

        if (isDead() && !deathRegistered) {
            registerDeath();
        }
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean isCorpse() {
        return isDead();
    }
    
    public void attack(Unit target) {
        if (attackTimer <= 0 && !isDead() && !target.isDead() && !isStunned()) {
            target.takeHit(attackDamage, this);
            attackTimer = attackCooldown;
            
            // Apply recoil to attacker
            Vector3 recoilDir = tmp.set(position).sub(target.getPosition()).nor();
            if (recoilDir.isZero(0.0001f)) {
                recoilDir.set(MathUtils.randomSign(), 0, 0);
            }
            float recoilStrength = getKnockbackStrengthAgainst(target) * 0.6f; // 60% of attack knockback
            applyKnockback(recoilDir, recoilStrength);
            
            // Apply stun to attacker
            hitStunTimer = HIT_STUN_DURATION;
            damageFlashTimer = DAMAGE_FLASH_DURATION * 0.5f; // Half duration flash for attacker
        }
    }

    protected float getBaseKnockbackStrength() {
        return BASE_KNOCKBACK_STRENGTH;
    }

    protected float getKnockbackStrengthAgainst(Unit target) {
        return getBaseKnockbackStrength();
    }

    protected void applyKnockback(Vector3 direction, float strength) {
        if (strength <= 0 || direction.isZero(0.0001f)) {
            return;
        }

        Vector3 planar = knockbackPlanarDir.set(direction.x, direction.y, 0f);
        if (planar.isZero(0.0001f)) {
            planar.set(MathUtils.randomSign(), 0f, 0f);
        }

        planar.nor();

        // Clear all horizontal velocity and set to knockback direction
        velocity.x = planar.x * strength;
        velocity.y = planar.y * strength;

        knockbackImpulse.add(planar.x * strength, planar.y * strength, 0f);

        float upwardImpulse = Math.max(MIN_VERTICAL_RECOIL, strength * 0.35f);
        if (velocity.z < upwardImpulse) {
            velocity.z = upwardImpulse;
        }
    }

    // Physics fields
    protected float speed = 4f;
    protected float gravity = 30f;
    protected boolean onGround = false;
    protected Vector3 tmp = new Vector3();

    protected boolean beginUpdate(float delta, GridWorld world) {
        if (attackTimer > 0) {
            attackTimer = Math.max(0f, attackTimer - delta);
        }
        if (damageFlashTimer > 0) {
            damageFlashTimer = Math.max(0f, damageFlashTimer - delta);
        }
        if (hitStunTimer > 0) {
            hitStunTimer = Math.max(0f, hitStunTimer - delta);
        }
        if (awarenessFreezeTimer > 0f) {
            awarenessFreezeTimer = Math.max(0f, awarenessFreezeTimer - delta);
        }
        if (awarenessIconTimer > 0f) {
            awarenessIconTimer = Math.max(0f, awarenessIconTimer - delta);
            if (awarenessIconTimer == 0f) {
                awarenessIcon = AwarenessIcon.NONE;
            }
        }

        if (awarenessStateTimer > 0f) {
            awarenessStateTimer = Math.max(0f, awarenessStateTimer - delta);
            if (awarenessStateTimer == 0f && awarenessState != AwarenessState.RELAXED) {
                awarenessState = AwarenessState.RELAXED;
            }
        }
        if (awarenessCooldownTimer > 0f) {
            awarenessCooldownTimer = Math.max(0f, awarenessCooldownTimer - delta);
            if (awarenessCooldownTimer == 0f) {
                awarenessLockState = AwarenessState.RELAXED;
            }
        }

        if (isDead()) {
            corpseTimer += delta;
            applyCorpsePhysics(delta, world);
            return false;
        }

        corpseTimer = 0f;
        return true;
    }

    protected void applyCorpsePhysics(float delta, GridWorld world) {
        velocity.x *= 0.9f;
        velocity.y *= 0.9f;
        applyPhysics(delta, world);
    }

    protected void applyPhysics(float delta, GridWorld world) {
        if (!knockbackImpulse.isZero(0.0001f)) {
            velocity.add(knockbackImpulse);
        }
        
        // Update facing if moving horizontally
        if (velocity.x * velocity.x + velocity.y * velocity.y > 0.01f) {
            facing.set(velocity.x, velocity.y, 0).nor();
        }

        // Apply gravity
        velocity.z -= gravity * delta;
        
        // Apply velocity
        float nextX = position.x + velocity.x * delta;
        float nextY = position.y + velocity.y * delta;
        float nextZ = position.z + velocity.z * delta;

        // Collision Detection
        boolean stepped = false;

        // Check X
        if (isValidPos(world, nextX, position.y, position.z)) {
            position.x = nextX;
        } else if (onGround && isValidPos(world, nextX, position.y, position.z + 1)) {
            // Auto-step up
            position.x = nextX;
            position.z += 1;
            stepped = true;
        } else {
            velocity.x = 0;
        }

        // Check Y
        if (isValidPos(world, position.x, nextY, position.z)) {
            position.y = nextY;
        } else if (onGround && !stepped && isValidPos(world, position.x, nextY, position.z + 1)) {
            // Auto-step up
            position.y = nextY;
            position.z += 1;
            stepped = true;
        } else {
            velocity.y = 0;
        }

        // Check Z (Gravity/Jumping)
        if (stepped) {
            velocity.z = 0;
            onGround = true;
        } else {
            if (isValidPos(world, position.x, position.y, nextZ)) {
                position.z = nextZ;
                onGround = false;
            } else {
                if (velocity.z < 0) {
                    onGround = true;
                    // Hit floor. Snap to the top of the block we hit.
                    // The block we hit is at floor(nextZ).
                    // We want to be at floor(nextZ) + 1.001f.
                    // However, if nextZ is very close to integer, floor might be the block below.
                    // If we are at 1.0 and nextZ is 0.9. floor(0.9) is 0. +1.001 is 1.001. Correct.
                    position.z = (float)Math.floor(nextZ) + 1.001f;
                    
                    // Backup check: if still invalid, push up more
                    if (!isValidPos(world, position.x, position.y, position.z)) {
                         position.z += 0.1f; 
                    }
                    velocity.z = 0; // Stop falling
                } else {
                    velocity.z = 0; // Hit ceiling
                }
            }
        }
        
        // Bounds check
        position.x = MathUtils.clamp(position.x, 0, world.getWidth() - 1);
        position.y = MathUtils.clamp(position.y, 0, world.getDepth() - 1);
        position.z = MathUtils.clamp(position.z, 0, world.getHeight() - 1);

        if (!knockbackImpulse.isZero(0.0001f)) {
            float decay = Math.max(0f, 1f - KNOCKBACK_DAMPING * delta);
            knockbackImpulse.scl(decay);
            if (knockbackImpulse.len2() < 0.01f) {
                knockbackImpulse.setZero();
            }
        }
    }

    protected boolean isValidPos(GridWorld world, float x, float y, float z) {
        // Collision radius to prevent camera clipping through walls
        float radius = 0.6f; // Player width/2
        
        int bz = (int)Math.floor(z); 
        int bzHead = (int)Math.floor(z + 1.5f);

        // Check center position
        int bx = Math.round(x);
        int by = Math.round(y);
        if (world.isSolid(world.getBlock(bx, by, bz))) return false;
        if (world.isSolid(world.getBlock(bx, by, bzHead))) return false;
        
        // Check 4 points around the entity at the collision radius
        // This prevents the player from getting so close to walls that the camera clips through
        float[][] offsets = {
            {radius, 0},
            {-radius, 0},
            {0, radius},
            {0, -radius}
        };
        
        for (float[] offset : offsets) {
            int checkX = Math.round(x + offset[0]);
            int checkY = Math.round(y + offset[1]);
            if (world.isSolid(world.getBlock(checkX, checkY, bz))) return false;
            if (world.isSolid(world.getBlock(checkX, checkY, bzHead))) return false;
        }
        
        return true;
    }

    protected void checkEnvironment(GridWorld world) {
        int x = Math.round(position.x);
        int y = Math.round(position.y);
        int z = Math.round(position.z);
        
        if (x >= 0 && x < world.getWidth() && y >= 0 && y < world.getDepth() && z >= 0 && z < world.getHeight()) {
            GridWorld.BlockState block = world.getBlock(x, y, z);
            if (block == GridWorld.BlockState.WATER) {
                hp = 0; // Instant death
            }
        }
        
        // Fall damage or void death?
        if (z < 0) hp = 0;

        if (isDead() && !deathRegistered) {
            registerDeath();
        }
    }

    protected Unit targetEnemy;

    public void scanForEnemies(java.util.List<Entity> entities, GridWorld world) {
        float closestDist = 10f; // Vision range
        Unit closest = null;
        
        for (Entity e : entities) {
            if (e instanceof Unit && e.getTeam() != this.team && !((Unit)e).isDead()) {
                float d = position.dst(e.getPosition());
                if (d < closestDist) {
                     closestDist = d;
                     closest = (Unit)e;
                }
            }
        }
        setTargetEnemy(closest);
    }

    public Unit getTargetEnemy() {
        return targetEnemy;
    }

    protected void setTargetEnemy(Unit newTarget) {
        if (targetEnemy == newTarget) {
            return;
        }
        Unit previous = this.targetEnemy;
        this.targetEnemy = newTarget;
        if (newTarget != null && (previous == null || previous != newTarget)) {
            onEnemySpotted(newTarget);
        }
    }

    protected void onEnemySpotted(Unit enemy) {
        triggerAwarenessCue(AwarenessIcon.ALERT, true);
    }

    public AwarenessIcon getAwarenessIcon() {
        return awarenessIcon;
    }

    public float getAwarenessIconAlpha() {
        if (awarenessIcon == AwarenessIcon.NONE) {
            return 0f;
        }
        return MathUtils.clamp(awarenessIconTimer / AWARENESS_ICON_DURATION, 0f, 1f);
    }

    public void triggerAwarenessCue(AwarenessIcon icon) {
        triggerAwarenessCue(icon, true);
    }

    public void triggerAwarenessCue(AwarenessIcon icon, boolean applyPause) {
        if (icon == null || icon == AwarenessIcon.NONE || isDead()) {
            if (icon == AwarenessIcon.NONE) {
                clearAwarenessCue();
            }
            return;
        }
        AwarenessState requestedState = mapIconToState(icon);
        if (!canTriggerAwarenessState(requestedState)) {
            return;
        }

        awarenessIcon = icon;
        awarenessIconTimer = AWARENESS_ICON_DURATION;
        enterAwarenessState(requestedState);
        if (applyPause) {
            awarenessFreezeTimer = Math.max(awarenessFreezeTimer, AWARENESS_FREEZE_DURATION);
        }
    }

    public void clearAwarenessCue() {
        awarenessIcon = AwarenessIcon.NONE;
        awarenessIconTimer = 0f;
        awarenessState = AwarenessState.RELAXED;
        awarenessStateTimer = 0f;
        awarenessCooldownTimer = 0f;
        awarenessLockState = AwarenessState.RELAXED;
    }

    public boolean isStunned() {
        return hitStunTimer > 0f || awarenessFreezeTimer > 0f;
    }

    public AwarenessState getAwarenessState() {
        return awarenessState;
    }

    public boolean isCuriousStateActive() {
        return awarenessState == AwarenessState.CURIOUS && awarenessStateTimer > 0f;
    }

    public boolean isSurprisedStateActive() {
        return awarenessState == AwarenessState.SURPRISED && awarenessStateTimer > 0f;
    }

    public void resetAwarenessCooldown() {
        awarenessCooldownTimer = 0f;
        awarenessState = AwarenessState.RELAXED;
        awarenessStateTimer = 0f;
        awarenessLockState = AwarenessState.RELAXED;
    }

    private AwarenessState mapIconToState(AwarenessIcon icon) {
        return switch (icon) {
            case ALERT -> AwarenessState.SURPRISED;
            case INVESTIGATE -> AwarenessState.CURIOUS;
            default -> AwarenessState.RELAXED;
        };
    }

    private boolean canTriggerAwarenessState(AwarenessState requested) {
        if (requested == AwarenessState.RELAXED) {
            return true;
        }
        if (awarenessState == requested && awarenessStateTimer > 0f) {
            return false;
        }
        if (awarenessCooldownTimer > 0f) {
            int requestedRank = awarenessStateRank(requested);
            int lockRank = awarenessStateRank(awarenessLockState);
            if (requestedRank <= lockRank) {
                return false;
            }
        }
        return true;
    }

    private int awarenessStateRank(AwarenessState state) {
        return switch (state) {
            case SURPRISED -> 2;
            case CURIOUS -> 1;
            default -> 0;
        };
    }

    private void enterAwarenessState(AwarenessState newState) {
        awarenessState = newState;
        switch (newState) {
            case CURIOUS -> awarenessStateTimer = CURIOUS_STATE_DURATION;
            case SURPRISED -> awarenessStateTimer = SURPRISED_STATE_DURATION;
            default -> awarenessStateTimer = 0f;
        }
        if (newState != AwarenessState.RELAXED) {
            awarenessCooldownTimer = AWARENESS_REARM_DURATION;
            awarenessLockState = newState;
        }
    }

    public float getDamageFlashAlpha() {
        return MathUtils.clamp(damageFlashTimer / DAMAGE_FLASH_DURATION, 0f, 1f);
    }

    public Vector3 getCorpseForward(Vector3 out) {
        return out.set(corpseForward);
    }

    public float getCorpseTimer() {
        return corpseTimer;
    }

    public float getCorpseLifetime() {
        return CORPSE_LIFETIME;
    }

    public boolean shouldDespawn() {
        return isDead() && corpseTimer >= CORPSE_LIFETIME;
    }

    private void registerDeath() {
        deathRegistered = true;
        corpseTimer = 0f;
        clearAwarenessCue();
        awarenessFreezeTimer = 0f;
        Vector3 planar = tmp.set(lastHitDirection.x, lastHitDirection.y, 0f);
        if (planar.isZero(0.0001f)) {
            planar.set(0, 1, 0);
        }
        corpseForward.set(planar.nor());
    }
}
