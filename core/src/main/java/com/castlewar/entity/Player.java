package com.castlewar.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public class Player extends Unit {
    private final PerspectiveCamera camera;
    private final Vector3 velocity = new Vector3();
    private final Vector3 tmp = new Vector3();
    
    private float speed = 8f;
    private float jumpForce = 12f;
    private float gravity = 30f;
    private boolean onGround = false;
    
    // Camera settings
    private float sensitivity = 0.2f;
    private float pitch = 0f;
    private float yaw = 0f;

    public Player(float x, float y, float z, Team team, PerspectiveCamera camera) {
        super(x, y, z, team, "Player", 100f, 100f);
        this.camera = camera;
        this.canClimb = false; // Player jumps, doesn't auto-climb walls like Assassin
    }

    @Override
    public void update(float delta, GridWorld world) {
        handleInput(delta);
        applyPhysics(delta, world);
        checkEnvironment(world); // Check after move
        updateCamera();
    }

    private void handleInput(float delta) {
        // Mouse look
        float deltaX = -Gdx.input.getDeltaX() * sensitivity;
        float deltaY = -Gdx.input.getDeltaY() * sensitivity;
        
        yaw += deltaX;
        pitch += deltaY;
        pitch = MathUtils.clamp(pitch, -89f, 89f);

        // Movement
        Vector3 forward = new Vector3(camera.direction.x, camera.direction.y, 0).nor();
        Vector3 right = new Vector3(forward.y, -forward.x, 0).nor(); // Perpendicular in 2D plane (Z is up)
        // Wait, coordinate system:
        // GridWorld: X=Width, Y=Depth, Z=Height (Up).
        // LibGDX Camera default: Y is Up?
        // I need to configure camera to have Z as Up.
        // Or just map my logic to camera's Y-up.
        // In DualViewScreen, renderEntitySide uses Z as height.
        // So Z is definitely Up in GridWorld.
        // I should configure PerspectiveCamera to have Z as Up.
        // camera.up.set(0, 0, 1);
        
        // Recalculate forward/right based on Z-up
        // Camera direction is 3D.
        // Flatten direction to XY plane for movement
        forward.set(camera.direction.x, camera.direction.y, 0).nor();
        right.set(camera.direction).crs(camera.up).nor();

        Vector3 moveDir = new Vector3();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveDir.add(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveDir.sub(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveDir.sub(right);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveDir.add(right);
        
        if (moveDir.len2() > 0) {
            moveDir.nor();
            velocity.x = moveDir.x * speed;
            velocity.y = moveDir.y * speed;
        } else {
            velocity.x = 0;
            velocity.y = 0;
        }

        // Jump
        if (onGround && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            velocity.z = jumpForce;
            onGround = false;
        }
    }

    private void applyPhysics(float delta, GridWorld world) {
        // Apply gravity
        velocity.z -= gravity * delta;
        
        // Apply velocity
        float nextX = position.x + velocity.x * delta;
        float nextY = position.y + velocity.y * delta;
        float nextZ = position.z + velocity.z * delta;

        // Collision Detection
        // We separate X and Y axes to allow sliding against walls
        
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
        // If we already stepped up in X, we are at z+1. 
        // We should check Y at the NEW z.
        if (isValidPos(world, position.x, nextY, position.z)) {
            position.y = nextY;
        } else if (onGround && !stepped && isValidPos(world, position.x, nextY, position.z + 1)) {
            // Auto-step up (only if we haven't already stepped this frame)
            position.y = nextY;
            position.z += 1;
            stepped = true;
        } else {
             // Try stepping if we already stepped? 
             // If we stepped in X, we are at Z+1.
             // If Y is BLOCKED at Z+1, can we step to Z+2?
             // That seems excessive (2 blocks per frame).
             // So we just stop Y if blocked at current Z.
             velocity.y = 0;
        }

        // Check Z (Gravity/Jumping)
        // If we stepped, we are already on ground at new Z.
        // But we still need to check if we hit a ceiling during the step?
        // For simplicity, if we stepped, we assume we are supported.
        
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
                    // Snap to nearest integer floor to prevent micro-bouncing
                    position.z = (float)Math.floor(position.z); 
                    // Ensure we are not inside the floor
                    if (!isValidPos(world, position.x, position.y, position.z)) {
                         position.z += 0.1f; // Push up slightly
                    }
                } else {
                    // Hit ceiling
                    velocity.z = 0;
                }
            }
        }
        
        // Bounds check
        position.x = MathUtils.clamp(position.x, 0, world.getWidth() - 1);
        position.y = MathUtils.clamp(position.y, 0, world.getDepth() - 1);
        position.z = MathUtils.clamp(position.z, 0, world.getHeight() - 1);
    }

    private boolean isValidPos(GridWorld world, float x, float y, float z) {
        // Check the block at the player's feet and head
        // Player height approx 1.8 blocks?
        // Let's assume 1 block width, 2 blocks height.
        
        int bx = Math.round(x);
        int by = Math.round(y);
        int bz = (int)Math.floor(z); // Feet
        int bzHead = (int)Math.floor(z + 1.5f); // Head

        // Check feet
        if (world.isSolid(world.getBlock(bx, by, bz))) return false;
        // Check head
        if (world.isSolid(world.getBlock(bx, by, bzHead))) return false;
        
        return true;
    }

    private void updateCamera() {
        camera.position.set(position.x, position.y, position.z + 1.6f); // Eye level
        
        // Update direction from yaw/pitch
        // Assuming Z is up
        Vector3 dir = new Vector3();
        dir.x = MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch);
        dir.y = MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch);
        dir.z = MathUtils.sinDeg(pitch);
        
        camera.direction.set(dir).nor();
        camera.up.set(0, 0, 1);
        camera.update();
    }
}
