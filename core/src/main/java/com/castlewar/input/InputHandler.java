package com.castlewar.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Consolidates input polling with state debouncing to prevent repeated triggers.
 */
public class InputHandler {
    private boolean keySlashPressed;
    private boolean keyMinusPressed;
    private boolean keyEqualsPressed;
    private boolean keyMPressed;
    private boolean keyIPressed;
    private boolean keyCommaPressed;
    private boolean keyPeriodPressed;
    private boolean keyLeftBracketPressed;
    private boolean keyRightBracketPressed;
    private boolean keyCPressed;
    private boolean keyXPressed;

    /**
     * Checks if a key was just pressed (with debouncing).
     * @param keyCode The key code to check
     * @return true if key was just pressed (not held)
     */
    public boolean isKeyJustPressed(int keyCode) {
        boolean currentlyPressed = Gdx.input.isKeyPressed(keyCode);
        boolean wasPressed = getKeyState(keyCode);
        
        setKeyState(keyCode, currentlyPressed);
        
        return currentlyPressed && !wasPressed;
    }

    /**
     * Updates the key state after checking. Should be called after processing input.
     */
    public void updateKeyState(int keyCode, boolean pressed) {
        setKeyState(keyCode, pressed);
    }

    private boolean getKeyState(int keyCode) {
        switch (keyCode) {
            case Input.Keys.SLASH: return keySlashPressed;
            case Input.Keys.MINUS: return keyMinusPressed;
            case Input.Keys.EQUALS: return keyEqualsPressed;
            case Input.Keys.M: return keyMPressed;
            case Input.Keys.I: return keyIPressed;
            case Input.Keys.COMMA: return keyCommaPressed;
            case Input.Keys.PERIOD: return keyPeriodPressed;
            case Input.Keys.LEFT_BRACKET: return keyLeftBracketPressed;
            case Input.Keys.RIGHT_BRACKET: return keyRightBracketPressed;
            case Input.Keys.C: return keyCPressed;
            case Input.Keys.X: return keyXPressed;
            default: return false;
        }
    }

    private void setKeyState(int keyCode, boolean pressed) {
        switch (keyCode) {
            case Input.Keys.SLASH: keySlashPressed = pressed; break;
            case Input.Keys.MINUS: keyMinusPressed = pressed; break;
            case Input.Keys.EQUALS: keyEqualsPressed = pressed; break;
            case Input.Keys.M: keyMPressed = pressed; break;
            case Input.Keys.I: keyIPressed = pressed; break;
            case Input.Keys.COMMA: keyCommaPressed = pressed; break;
            case Input.Keys.PERIOD: keyPeriodPressed = pressed; break;
            case Input.Keys.LEFT_BRACKET: keyLeftBracketPressed = pressed; break;
            case Input.Keys.RIGHT_BRACKET: keyRightBracketPressed = pressed; break;
            case Input.Keys.C: keyCPressed = pressed; break;
            case Input.Keys.X: keyXPressed = pressed; break;
        }
    }
}
