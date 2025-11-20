package com.castlewar.ai;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.castlewar.entity.Unit;

/**
 * Base class that wires a Unit to gdx-ai state machine and behavior tree components.
 * Concrete NPCs can extend this helper to keep their update methods small while we migrate
 * existing logic piece by piece.
 */
public abstract class AiAgent<T extends Unit> {
    protected final T owner;
    protected final AiContext context;

    private StateMachine<T, State<T>> stateMachine;
    private BehaviorTree<T> behaviorTree;

    protected AiAgent(T owner, AiContext context) {
        this.owner = owner;
        this.context = context;
    }

    public void update(float delta) {
        if (stateMachine != null) {
            stateMachine.update();
        }
        if (behaviorTree != null) {
            behaviorTree.step();
        }
    }

    public T getOwner() {
        return owner;
    }

    public AiContext getContext() {
        return context;
    }

    public StateMachine<T, State<T>> getStateMachine() {
        return stateMachine;
    }

    @SuppressWarnings("unchecked")
    public void setStateMachine(StateMachine<T, ? extends State<T>> stateMachine) {
        this.stateMachine = (StateMachine<T, State<T>>) stateMachine;
    }

    public BehaviorTree<T> getBehaviorTree() {
        return behaviorTree;
    }

    public void setBehaviorTree(BehaviorTree<T> behaviorTree) {
        this.behaviorTree = behaviorTree;
    }
}
