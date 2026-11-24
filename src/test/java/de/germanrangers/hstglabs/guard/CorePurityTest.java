package de.germanrangers.hstglabs.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Guardrail 3 (docs/design.md §12.2): core/ is pure Java. */
class CorePurityTest {

    @Test
    void sourceTreeCoreIsPure() {
        List<String> violations = BoundaryRules.coreViolations(SourceScanner.scan(SourceScanner.mainSources()));
        assertEquals(List.of(), violations, "core purity violations");
    }

    @Test
    void plantedMinecraftImportInCoreFails() {
        var source = BoundaryRules.planted("core/Bad.java", """
              package de.germanrangers.hstglabs.core;
              import net.minecraft.world.item.ItemStack;
              class Bad {}
              """);
        assertFalse(BoundaryRules.coreViolations(List.of(source)).isEmpty());
    }

    @Test
    void plantedForbiddenPrefixesInCoreFail() {
        for (String ref : List.of("net.neoforged.bus.api.Event", "net.bananemdnsa.historystages.api.HistoryStagesAPI",
              "mekanism.api.Action", "com.mojang.serialization.Codec")) {
            var source = BoundaryRules.planted("core/Bad.java",
                  "package de.germanrangers.hstglabs.core;\nclass Bad { Object o = " + ref + ".class; }\n");
            assertFalse(BoundaryRules.coreViolations(List.of(source)).isEmpty(), ref);
        }
    }

    @Test
    void plantedJdkOnlyCorePasses() {
        var source = BoundaryRules.planted("core/Ok.java", """
              package de.germanrangers.hstglabs.core;
              import java.util.List;
              class Ok { java.util.Map<String, List<Integer>> m; }
              """);
        assertEquals(List.of(), BoundaryRules.coreViolations(List.of(source)));
    }
}
