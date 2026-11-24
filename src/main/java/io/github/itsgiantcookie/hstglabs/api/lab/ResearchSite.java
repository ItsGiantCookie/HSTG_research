package io.github.itsgiantcookie.hstglabs.api.lab;

import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Read-only view of one research site (docs/design.md §5, §9.3): a basic lab, a network, or later a
 * multiblock. What {@link LabRequirementType}s test.
 */
@ApiStatus.Experimental
public interface ResearchSite {

    /** The owner this site researches for. */
    OwnerId owner();

    /** True for a distributed network, false for a single block. */
    boolean isNetwork();

    /** Tier of the site's controller; caps every worker slot (D29). */
    int controllerTier();

    /** Every member, active or not. */
    List<SiteMember> members();

    /** Active members only (D19). */
    default Stream<SiteMember> activeMembers() {
        return members().stream().filter(SiteMember::active);
    }
}
