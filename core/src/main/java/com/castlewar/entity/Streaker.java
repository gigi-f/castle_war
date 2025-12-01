package com.castlewar.entity;

import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public class Streaker extends Unit {
    private Vector3 leftTarget;
    private Vector3 rightTarget;
    private boolean goingRight = true;

    public Streaker(float x, float y, float z, Team team, Vector3 leftTarget, Vector3 rightTarget) {
        super(x, y, z, team, "Streaker", 100f, 100f);
        this.leftTarget = leftTarget;
        this.rightTarget = rightTarget;
        this.speed = 6.0f; // Fast!
        this.canClimb = true; // Why not?
    }

    @Override
    public void update(float delta, GridWorld world) {
        checkEnvironment(world);
        if (!beginUpdate(delta, world)) {
            return;
        }
        
        if (isStunned()) {
            // Freeze during stun
            velocity.x = 0;
            velocity.y = 0;
        } else {
            Vector3 currentTarget = goingRight ? rightTarget : leftTarget;
            
            // Check if reached
            if (position.dst(currentTarget) < 2.0f) {
                goingRight = !goingRight;
                currentTarget = goingRight ? rightTarget : leftTarget;
                // (movement disabled) previously cleared path here
            }
            
            // Movement disabled: Streaker remains stationary.
            // Clear any planned target and ensure zero velocity.
            targetPosition = null;
            velocity.set(0f, 0f, 0f);
        }
        
        super.applyPhysics(delta, world);
    }

    @Override
    protected float getKnockbackStrengthAgainst(Unit target) {
        return 6.0f;
    }
}
