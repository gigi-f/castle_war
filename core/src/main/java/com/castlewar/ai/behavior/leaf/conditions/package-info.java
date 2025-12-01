/**
 * Shared condition nodes for behavior trees.
 * <p>
 * These conditions check blackboard state and return SUCCESS/FAILURE
 * based on unit status, combat state, and environmental factors.
 * 
 * <h2>Categories</h2>
 * <ul>
 *   <li>Self Conditions - Health, state, position checks</li>
 *   <li>Target Conditions - Has target, target alive, in range</li>
 *   <li>Threat Conditions - Under attack, enemies nearby</li>
 *   <li>Environmental Conditions - At position, path exists</li>
 * </ul>
 */
package com.castlewar.ai.behavior.leaf.conditions;
