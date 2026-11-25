package io.github.itsgiantcookie.hstglabs.lab;

import com.google.gson.JsonObject;
import java.util.Set;

/**
 * One lab type (docs/design.md §5.5): what a lab block is. Stats are data and change with
 * {@code /reload} (D17).
 *
 * @param id             lab type id
 * @param roles          {@code controller}, {@code consumer}, {@code worker}, {@code node}
 * @param tier           lab tier (§5.9)
 * @param speed          unit speed at no modifiers
 * @param parallel       worker slots
 * @param energyPerTick  Joules per running slot per tick (D6, D23)
 * @param energyCapacity Joule buffer
 * @param itemSlots      consumer item slots
 * @param accepts        ingredient type ids a consumer accepts
 * @param tags           lab tags, e.g. {@code hstg_labs:chemistry}
 * @param upgrades       upgrade kinds that fit: {@code speed}, {@code energy}
 * @param maxConsumers   controller capacity (§5.3)
 * @param maxWorkers     controller capacity
 * @param json           the merged JSON, unknown and addon data included
 */
public record LabTypeDefinition(String id, Set<String> roles, int tier, double speed, int parallel, long energyPerTick, long energyCapacity,
      int itemSlots, Set<String> accepts, Set<String> tags, Set<String> upgrades, int maxConsumers, int maxWorkers, JsonObject json) {

    public LabTypeDefinition {
        roles = Set.copyOf(roles);
        accepts = Set.copyOf(accepts);
        tags = Set.copyOf(tags);
        upgrades = Set.copyOf(upgrades);
        json = json.deepCopy();
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }
}
