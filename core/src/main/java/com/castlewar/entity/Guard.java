package com.castlewar.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.ai.AiContext;
import com.castlewar.ai.guard.GuardBTAgent;
import com.castlewar.ai.guard.GuardState;
import com.castlewar.simulation.WorldContext;
import com.castlewar.simulation.WorldContext.CastleBounds;
import com.castlewar.world.GridWorld;

public class Guard extends Unit {
    public enum GuardType {
        ENTOURAGE,
        PATROL
    }

    private static final String[] NAMES = {"Guard", "Sentry", "Warden", "Protector", "Shield", "Knight"};
    private static final float ALERT_MEMORY_DURATION = 5f;
    private static final int SEARCH_ATTEMPTS = 12;
    private static final float MIN_PATROL_SPACING = 8f;

    private final GuardType type;
    private final GuardBTAgent aiAgent;
    private Entity targetToFollow; // For Entourage
    private final Vector3 lastKnownEnemyPosition = new Vector3();
    private boolean hasLastKnownEnemy;
    private float alertMemoryTimer;
    private final Vector3 tempVector = new Vector3();
    private final Vector3 separationVector = new Vector3();
    private transient float aiDeltaSnapshot;

    public Guard(float x, float y, float z, Team team, GuardType type, WorldContext worldContext) {
        super(x, y, z, team, generateName(type), 80f, 50f);
        this.type = type;
        this.aiAgent = new GuardBTAgent(this, new AiContext(worldContext));
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
        aiAgent.update(delta, aiAgent.getContext());

        super.applyPhysics(delta, world);
    }

    public void updateAttack() {
        if (!isStunned() && targetEnemy != null && !targetEnemy.isDead()) {
            rememberEnemySighting(targetEnemy.getPosition());
            float dist = position.dst(targetEnemy.getPosition());
            if (dist < attackRange) {
                attack(targetEnemy);
                velocity.x = 0;
                velocity.y = 0;
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

    public void stopMoving() {
        velocity.x = 0;
        velocity.y = 0;
        clearTargetPosition("STOP_COMMAND");
    }

    public GuardBTAgent getAiAgent() {
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

        if (targetEnemy == null) {
            triggerAwarenessCue(AwarenessIcon.INVESTIGATE);
        }
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

    public boolean needsToFlee() {
        return hp < maxHp * 0.3f;
    }

    public GuardState getCurrentState() {
        return aiAgent.getCurrentState();
    }

    public String getCurrentStateName() {
        GuardState current = getCurrentState();
        return current != null ? current.name() : "UNKNOWN";
    }

    public void changeState(GuardState nextState) {
        GuardState previous = getCurrentState();
        String detail = (previous != null ? previous.name() : "NONE") + " -> " + nextState.name();
        logGuardEvent("STATE", detail, null);
        aiAgent.changeState(nextState);
    }

    private void logGuardEvent(String category, String detail, Vector3 destination) {
        AiContext ctx = aiAgent.getContext();
        if (ctx == null) {
            return;
        }
        WorldContext worldContext = ctx.getWorldContext();
        if (worldContext == null || worldContext.getDebugLog() == null) {
            return;
        }
        worldContext.getDebugLog().logGuardEvent(this, category, detail, position, destination);
    }
}
