package com.castlewar.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Controls an orthographic camera with pan, zoom, and rotation.
 * Each viewport can have its own controller for independent manipulation.
 */
public class CameraController extends InputAdapter {
    private final OrthographicCamera camera;
    private final Vector3 focusPoint;
    private final boolean isSideView;
    
    private float zoom = 1.0f;
    private float rotation = 0f; // Rotation around the focus point
    private final float panSpeed = 2.0f;
    private final float zoomSpeed = 0.1f;
    private final float rotateSpeed = 2.0f;
    
    private final Vector2 lastTouch = new Vector2();
    private boolean isPanning = false;

    /**
     * Creates a camera controller.
     * @param camera The camera to control
     * @param focusPoint The point in 3D space both cameras look at (synchronized)
     * @param isSideView True if this is the side view, false for top-down view
     */
    public CameraController(OrthographicCamera camera, Vector3 focusPoint, boolean isSideView) {
        this.camera = camera;
        this.focusPoint = focusPoint;
        this.isSideView = isSideView;
    }

    /**
     * Update camera controls based on keyboard/mouse input.
     */
    public void update(float delta) {
        // Pan with WASD keys
        float moveX = 0;
        float moveY = 0;
        
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            moveX -= panSpeed * delta * zoom;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            moveX += panSpeed * delta * zoom;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            moveY += panSpeed * delta * zoom;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            moveY -= panSpeed * delta * zoom;
        }
        
        // Apply panning
        if (moveX != 0 || moveY != 0) {
            pan(moveX, moveY);
        }
        
        // Rotate with Q/E keys
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            rotate(-rotateSpeed * delta * 60);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            rotate(rotateSpeed * delta * 60);
        }
        
        camera.update();
    }

    private void pan(float deltaX, float deltaY) {
        // Transform screen-space deltas to world-space based on current rotation
        float cos = MathUtils.cosDeg(rotation);
        float sin = MathUtils.sinDeg(rotation);
        
        float worldDX = deltaX * cos - deltaY * sin;
        float worldDY = deltaX * sin + deltaY * cos;
        
        focusPoint.x += worldDX;
        
        if (isSideView) {
            // Side view: Y is depth, keep it fixed; adjust Z for up/down
            focusPoint.z += worldDY;
        } else {
            // Top-down view: Y is vertical on screen
            focusPoint.y += worldDY;
        }
        
        updateCameraPosition();
    }

    private void rotate(float degrees) {
        rotation += degrees;
        rotation = rotation % 360;
        updateCameraPosition();
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        // Zoom in/out with scroll wheel
        zoom += amountY * zoomSpeed;
        zoom = MathUtils.clamp(zoom, 0.2f, 5.0f);
        camera.zoom = zoom;
        camera.update();
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            lastTouch.set(screenX, screenY);
            isPanning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            isPanning = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (isPanning) {
            float deltaX = (screenX - lastTouch.x) * 0.1f * zoom;
            float deltaY = (lastTouch.y - screenY) * 0.1f * zoom;
            pan(deltaX, deltaY);
            lastTouch.set(screenX, screenY);
            return true;
        }
        return false;
    }

    private void updateCameraPosition() {
        if (isSideView) {
            // Side view: looking at X/Z plane (from -Y direction)
            // Camera position is at the focus point projected onto X/Z plane
            camera.position.set(focusPoint.x, -200, focusPoint.z);
            camera.lookAt(focusPoint.x, 0, focusPoint.z);
            camera.up.set(0, 0, 1); // Z is up for side view
        } else {
            // Top-down view: looking at X/Y plane (from +Z direction)
            // Camera position is directly above the focus point
            camera.position.set(focusPoint.x, focusPoint.y, 200);
            camera.lookAt(focusPoint.x, focusPoint.y, 0);
            camera.up.set(0, 1, 0); // Y is up for top view
        }
        camera.update();
    }

    /**
     * Get the shared focus point that both cameras look at.
     */
    public Vector3 getFocusPoint() {
        return focusPoint;
    }

    /**
     * Reset camera to default position and zoom.
     */
    public void reset() {
        zoom = 1.0f;
        rotation = 0f;
        camera.zoom = zoom;
        updateCameraPosition();
    }
}
