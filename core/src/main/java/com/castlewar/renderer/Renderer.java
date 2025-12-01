package com.castlewar.renderer;

import com.badlogic.gdx.utils.Disposable;

/**
 * Interface for rendering components.
 * Implementing classes should handle their own resource disposal.
 */
public interface Renderer extends Disposable {
    /**
     * Renders the component.
     * @param delta Time elapsed since last render in seconds
     */
    void render(float delta);
}
