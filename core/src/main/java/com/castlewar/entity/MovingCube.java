package com.castlewar.entity;

/**
 * A simple entity that moves in the world.
 */
public class MovingCube {
    private float x;
    private float y;
    private float z;
    
    private float velocityX;
    private float minX;
    private float maxX;
    
    public MovingCube(float startX, float y, float z, float speed, float minX, float maxX) {
        this.x = startX;
        this.y = y;
        this.z = z;
        this.velocityX = speed;
        this.minX = minX;
        this.maxX = maxX;
    }
    
    public void update(float delta) {
        x += velocityX * delta;
        
        // Bounce at boundaries
        if (x <= minX) {
            x = minX;
            velocityX = Math.abs(velocityX);
        } else if (x >= maxX) {
            x = maxX;
            velocityX = -Math.abs(velocityX);
        }
    }
    
    public float getX() {
        return x;
    }
    
    public float getY() {
        return y;
    }
    
    public float getZ() {
        return z;
    }
}
