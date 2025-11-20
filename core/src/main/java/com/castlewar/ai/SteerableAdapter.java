package com.castlewar.ai;

import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Unit;

/**
 * Minimal {@link Steerable} implementation that bridges a Unit's 3D position/velocity
 * to the 2D vectors expected by gdx-ai steering behaviors (we only steer on the X/Y plane).
 */
public class SteerableAdapter implements Steerable<Vector2> {
    private final Unit unit;
    private final Vector2 position2d = new Vector2();
    private final Vector2 linearVelocity2d = new Vector2();
    private final Vector2 tmp = new Vector2();

    private float zeroLinearSpeedThreshold = 0.01f;
    private float boundingRadius = 0.6f;
    private boolean tagged;
    private float orientation;

    private float maxLinearSpeed = 8f;
    private float maxLinearAcceleration = 50f;
    private float maxAngularSpeed = 720f;
    private float maxAngularAcceleration = 720f;

    public SteerableAdapter(Unit unit) {
        this.unit = unit;
    }

    public void applySteering(Vector2 linearAcceleration, float delta) {
        if (linearAcceleration == null || linearAcceleration.isZero()) {
            return;
        }
        Vector3 velocity3 = unit.getVelocity();
        velocity3.x += linearAcceleration.x * delta;
        velocity3.y += linearAcceleration.y * delta;

        Vector2 planar = tmp.set(velocity3.x, velocity3.y);
        if (planar.len2() > maxLinearSpeed * maxLinearSpeed) {
            planar.clamp(0f, maxLinearSpeed);
            velocity3.x = planar.x;
            velocity3.y = planar.y;
        }
        orientation = planar.isZero(zeroLinearSpeedThreshold)
            ? orientation
            : vectorToAngle(planar);
    }

    @Override
    public Vector2 getLinearVelocity() {
        Vector3 v = unit.getVelocity();
        return linearVelocity2d.set(v.x, v.y);
    }

    @Override
    public float getAngularVelocity() {
        // We currently steer only in the plane, so angular velocity is derived elsewhere.
        return 0f;
    }

    @Override
    public float getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public boolean isTagged() {
        return tagged;
    }

    @Override
    public void setTagged(boolean tagged) {
        this.tagged = tagged;
    }

    @Override
    public Vector2 getPosition() {
        Vector3 p = unit.getPosition();
        return position2d.set(p.x, p.y);
    }

    @Override
    public float getOrientation() {
        if (Math.abs(orientation) < MathUtils.FLOAT_ROUNDING_ERROR) {
            Vector2 velocity = getLinearVelocity();
            if (!velocity.isZero(zeroLinearSpeedThreshold)) {
                orientation = vectorToAngle(velocity);
            }
        }
        return orientation;
    }

    @Override
    public void setOrientation(float orientation) {
        this.orientation = orientation;
    }

    @Override
    public float vectorToAngle(Vector2 vector) {
        return MathUtils.atan2(vector.y, vector.x);
    }

    @Override
    public Vector2 angleToVector(Vector2 outVector, float angle) {
        return outVector.set(MathUtils.cos(angle), MathUtils.sin(angle));
    }

    @Override
    public float getZeroLinearSpeedThreshold() {
        return zeroLinearSpeedThreshold;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {
        this.zeroLinearSpeedThreshold = Math.max(0f, value);
    }

    @Override
    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }

    @Override
    public void setMaxLinearSpeed(float maxLinearSpeed) {
        this.maxLinearSpeed = maxLinearSpeed;
    }

    @Override
    public float getMaxLinearAcceleration() {
        return maxLinearAcceleration;
    }

    @Override
    public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        this.maxLinearAcceleration = maxLinearAcceleration;
    }

    @Override
    public float getMaxAngularSpeed() {
        return maxAngularSpeed;
    }

    @Override
    public void setMaxAngularSpeed(float maxAngularSpeed) {
        this.maxAngularSpeed = maxAngularSpeed;
    }

    @Override
    public float getMaxAngularAcceleration() {
        return maxAngularAcceleration;
    }

    @Override
    public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        this.maxAngularAcceleration = maxAngularAcceleration;
    }

    public Unit getUnit() {
        return unit;
    }

    @Override
    public Location<Vector2> newLocation() {
        return new AdapterLocation();
    }

    private static final class AdapterLocation implements Location<Vector2> {
        private final Vector2 position = new Vector2();
        private float orientation;

        @Override
        public Vector2 getPosition() {
            return position;
        }

        @Override
        public float getOrientation() {
            return orientation;
        }

        @Override
        public void setOrientation(float orientation) {
            this.orientation = orientation;
        }

        @Override
        public Location<Vector2> newLocation() {
            return new AdapterLocation();
        }

        @Override
        public float vectorToAngle(Vector2 vector) {
            return MathUtils.atan2(vector.y, vector.x);
        }

        @Override
        public Vector2 angleToVector(Vector2 outVector, float angle) {
            return outVector.set(MathUtils.cos(angle), MathUtils.sin(angle));
        }
    }
}
