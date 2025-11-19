package com.castlewar.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.world.GridWorld;

public class Player extends Unit {
    private final PerspectiveCamera camera;
    // velocity and tmp are inherited from Entity/Unit
    
    private float jumpForce = 12f;
    
    // Camera settings
    private float sensitivity = 0.2f;
    private float pitch = 0f;
    private float yaw = 0f;

    public Player(float x, float y, float z, Team team, PerspectiveCamera camera) {
        super(x, y, z, team, "Player", 100f, 100f);
        this.camera = camera;
        this.canClimb = false; // Player jumps, doesn't auto-climb walls like Assassin
        this.speed = 8f; // Player is faster
    }

    @Override
    public void update(float delta, GridWorld world) {
        handleInput(delta);
        super.applyPhysics(delta, world);
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
