package com.castlewar.entity;

public abstract class Unit extends Entity {
    protected String name;
    protected float hp;
    protected float maxHp;
    protected float stamina;
    protected float maxStamina;

    public Unit(float x, float y, float z, Team team, String name, float maxHp, float maxStamina) {
        super(x, y, z, team);
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.maxStamina = maxStamina;
        this.stamina = maxStamina;
    }

    public String getName() { return name; }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp; }
}
