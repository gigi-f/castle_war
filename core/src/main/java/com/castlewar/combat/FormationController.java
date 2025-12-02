package com.castlewar.combat;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.castlewar.entity.Team;
import com.castlewar.entity.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls formation positioning for groups of units.
 * 
 * <h2>Formation Types</h2>
 * <ul>
 *   <li><b>LINE</b> - Standard battle line, units spread horizontally</li>
 *   <li><b>WEDGE</b> - V-shape for cavalry charges</li>
 *   <li><b>SQUARE</b> - Defensive formation against cavalry</li>
 *   <li><b>COLUMN</b> - Marching formation, narrow for pathways</li>
 *   <li><b>SCATTERED</b> - Loose formation to reduce AoE damage</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * FormationController formation = new FormationController(FormationType.LINE, leader);
 * formation.addMember(infantry1);
 * formation.addMember(infantry2);
 * 
 * // Get each unit's target position
 * Vector3 slot = formation.getSlotPosition(0); // First member
 * </pre>
 */
public class FormationController {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATION TYPES
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum FormationType {
        /** Standard battle line - units spread horizontally */
        LINE,
        /** V-shape for cavalry charges - leader at front */
        WEDGE,
        /** Defensive square - protects from all sides */
        SQUARE,
        /** Narrow marching column - for pathways */
        COLUMN,
        /** Loose spacing - reduces AoE effectiveness */
        SCATTERED
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Default spacing between units in LINE formation */
    private static final float LINE_SPACING = 2.5f;
    
    /** Spacing for WEDGE formation (wider) */
    private static final float WEDGE_SPACING = 3.0f;
    
    /** Spacing for SQUARE formation */
    private static final float SQUARE_SPACING = 2.0f;
    
    /** Spacing for COLUMN formation (tight) */
    private static final float COLUMN_SPACING = 2.0f;
    
    /** Spacing for SCATTERED formation (very wide) */
    private static final float SCATTERED_SPACING = 5.0f;
    
    /** Maximum units per row in SQUARE formation */
    private static final int SQUARE_ROW_SIZE = 4;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private FormationType currentFormation;
    private final List<Unit> members = new ArrayList<>();
    private Unit leader;
    private Vector3 formationCenter = new Vector3();
    private Vector3 formationFacing = new Vector3(0, 1, 0); // Default facing north
    private Team team;
    
    /** Cached slot positions to avoid allocation */
    private final List<Vector3> slotPositions = new ArrayList<>();
    private boolean slotsNeedUpdate = true;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new formation controller.
     * 
     * @param type   Initial formation type
     * @param leader Formation leader (determines center position)
     */
    public FormationController(FormationType type, Unit leader) {
        this.currentFormation = type;
        this.leader = leader;
        this.team = leader != null ? leader.getTeam() : Team.WHITE;
        if (leader != null) {
            formationCenter.set(leader.getPosition());
        }
    }
    
    public FormationController(FormationType type) {
        this(type, null);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MEMBER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Adds a unit to the formation.
     */
    public void addMember(Unit unit) {
        if (!members.contains(unit)) {
            members.add(unit);
            slotsNeedUpdate = true;
        }
    }
    
    /**
     * Removes a unit from the formation.
     */
    public void removeMember(Unit unit) {
        if (members.remove(unit)) {
            slotsNeedUpdate = true;
        }
    }
    
    /**
     * Sets the formation leader.
     */
    public void setLeader(Unit leader) {
        this.leader = leader;
        if (leader != null) {
            this.team = leader.getTeam();
        }
        slotsNeedUpdate = true;
    }
    
    /**
     * Gets the number of members in the formation.
     */
    public int getMemberCount() {
        return members.size();
    }
    
    /**
     * Gets the slot index for a specific unit.
     * Returns -1 if unit is not in formation.
     */
    public int getSlotIndex(Unit unit) {
        return members.indexOf(unit);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATION CONTROL
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Changes the formation type.
     * Units will need to move to new positions.
     */
    public void changeFormation(FormationType type) {
        if (this.currentFormation != type) {
            this.currentFormation = type;
            slotsNeedUpdate = true;
        }
    }
    
    /**
     * Updates the formation center position.
     */
    public void setCenter(Vector3 center) {
        formationCenter.set(center);
        slotsNeedUpdate = true;
    }
    
    /**
     * Updates the formation facing direction.
     */
    public void setFacing(Vector3 facing) {
        formationFacing.set(facing).nor();
        formationFacing.z = 0; // Keep facing horizontal
        slotsNeedUpdate = true;
    }
    
    /**
     * Updates formation based on leader position and facing.
     */
    public void updateFromLeader() {
        if (leader != null) {
            formationCenter.set(leader.getPosition());
            formationFacing.set(leader.getFacing());
            slotsNeedUpdate = true;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SLOT POSITION CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the target position for a slot in the formation.
     * 
     * @param slotIndex Index of the slot (0 to memberCount-1)
     * @return World position for that slot, or formation center if invalid
     */
    public Vector3 getSlotPosition(int slotIndex) {
        updateSlotPositions();
        
        if (slotIndex < 0 || slotIndex >= slotPositions.size()) {
            return formationCenter;
        }
        
        return slotPositions.get(slotIndex);
    }
    
    /**
     * Gets the target position for a specific unit.
     */
    public Vector3 getSlotPosition(Unit unit) {
        int index = getSlotIndex(unit);
        return getSlotPosition(index);
    }
    
    /**
     * Recalculates slot positions if needed.
     */
    private void updateSlotPositions() {
        if (!slotsNeedUpdate) return;
        
        // Ensure we have enough slot vectors
        while (slotPositions.size() < members.size()) {
            slotPositions.add(new Vector3());
        }
        
        // Calculate perpendicular (right) vector
        Vector3 right = new Vector3(-formationFacing.y, formationFacing.x, 0);
        
        // Calculate positions based on formation type
        switch (currentFormation) {
            case LINE -> calculateLinePositions(right);
            case WEDGE -> calculateWedgePositions(right);
            case SQUARE -> calculateSquarePositions(right);
            case COLUMN -> calculateColumnPositions();
            case SCATTERED -> calculateScatteredPositions(right);
        }
        
        slotsNeedUpdate = false;
    }
    
    private void calculateLinePositions(Vector3 right) {
        int count = members.size();
        float totalWidth = (count - 1) * LINE_SPACING;
        float startOffset = -totalWidth / 2f;
        
        for (int i = 0; i < count; i++) {
            float offset = startOffset + i * LINE_SPACING;
            slotPositions.get(i).set(formationCenter)
                .add(right.x * offset, right.y * offset, 0);
        }
    }
    
    private void calculateWedgePositions(Vector3 right) {
        int count = members.size();
        
        for (int i = 0; i < count; i++) {
            int row = i / 2;
            int side = (i % 2 == 0) ? -1 : 1;
            
            float forwardOffset = -row * WEDGE_SPACING;
            float sideOffset = (row + 1) * WEDGE_SPACING * 0.5f * side;
            
            if (i == 0) {
                // Leader at the tip
                slotPositions.get(i).set(formationCenter);
            } else {
                slotPositions.get(i).set(formationCenter)
                    .add(formationFacing.x * forwardOffset, formationFacing.y * forwardOffset, 0)
                    .add(right.x * sideOffset, right.y * sideOffset, 0);
            }
        }
    }
    
    private void calculateSquarePositions(Vector3 right) {
        int count = members.size();
        int rowSize = Math.min(SQUARE_ROW_SIZE, count);
        
        for (int i = 0; i < count; i++) {
            int row = i / rowSize;
            int col = i % rowSize;
            
            float totalRowWidth = (Math.min(rowSize, count - row * rowSize) - 1) * SQUARE_SPACING;
            float colOffset = -totalRowWidth / 2f + col * SQUARE_SPACING;
            float rowOffset = -row * SQUARE_SPACING;
            
            slotPositions.get(i).set(formationCenter)
                .add(formationFacing.x * rowOffset, formationFacing.y * rowOffset, 0)
                .add(right.x * colOffset, right.y * colOffset, 0);
        }
    }
    
    private void calculateColumnPositions() {
        int count = members.size();
        
        for (int i = 0; i < count; i++) {
            float offset = -i * COLUMN_SPACING;
            slotPositions.get(i).set(formationCenter)
                .add(formationFacing.x * offset, formationFacing.y * offset, 0);
        }
    }
    
    private void calculateScatteredPositions(Vector3 right) {
        int count = members.size();
        
        // Use deterministic pseudo-random offsets based on index
        for (int i = 0; i < count; i++) {
            float angle = (i * 137.5f) * MathUtils.degreesToRadians; // Golden angle
            float radius = SCATTERED_SPACING * (0.5f + (i % 3) * 0.3f);
            
            float xOffset = MathUtils.cos(angle) * radius;
            float yOffset = MathUtils.sin(angle) * radius;
            
            slotPositions.get(i).set(formationCenter)
                .add(xOffset, yOffset, 0);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // FORMATION COMMANDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Moves the entire formation forward.
     */
    public void advance(float distance) {
        formationCenter.add(
            formationFacing.x * distance,
            formationFacing.y * distance,
            0
        );
        slotsNeedUpdate = true;
    }
    
    /**
     * Rotates the formation facing.
     * 
     * @param degrees Rotation in degrees (positive = counter-clockwise)
     */
    public void rotate(float degrees) {
        float radians = degrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(radians);
        float sin = MathUtils.sin(radians);
        
        float newX = formationFacing.x * cos - formationFacing.y * sin;
        float newY = formationFacing.x * sin + formationFacing.y * cos;
        
        formationFacing.x = newX;
        formationFacing.y = newY;
        formationFacing.nor();
        
        slotsNeedUpdate = true;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public FormationType getCurrentFormation() {
        return currentFormation;
    }
    
    public Unit getLeader() {
        return leader;
    }
    
    public Vector3 getCenter() {
        return formationCenter;
    }
    
    public Vector3 getFacing() {
        return formationFacing;
    }
    
    public Team getTeam() {
        return team;
    }
    
    public List<Unit> getMembers() {
        return members;
    }
    
    /**
     * Checks if a unit is close enough to their assigned slot.
     */
    public boolean isInFormation(Unit unit, float tolerance) {
        Vector3 slot = getSlotPosition(unit);
        return unit.getPosition().dst(slot) <= tolerance;
    }
    
    /**
     * Checks if all units are in formation.
     */
    public boolean isFormationIntact(float tolerance) {
        for (Unit member : members) {
            if (!isInFormation(member, tolerance)) {
                return false;
            }
        }
        return true;
    }
}
