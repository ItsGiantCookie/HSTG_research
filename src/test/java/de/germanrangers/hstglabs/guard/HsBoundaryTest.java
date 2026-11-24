package de.germanrangers.hstglabs.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Guardrail 1 (docs/design.md §12.2): History Stages stays behind hs/ and its public api. */
class HsBoundaryTest {

    @Test
    void sourceTreeRespectsHsBoundary() {
        List<String> violations = BoundaryRules.hsViolations(SourceScanner.scan(SourceScanner.mainSources()));
        assertEquals(List.of(), violations, "History Stages boundary violations");
    }

    @Test
    void sourceTreeActuallyUsesHs() {
        // proves the scanner sees our HS usage at all (hs/HsVersion), so a green result means something
        boolean found = SourceScanner.scan(SourceScanner.mainSources()).stream()
              .anyMatch(s -> !s.references(BoundaryRules.HS_API).isEmpty());
        assertTrue(found, "expected at least one History Stages api reference in src/main/java");
    }

    @Test
    void plantedImportOutsideHsPackageFails() {
        var source = BoundaryRules.planted("lab/Bad.java", """
              package de.germanrangers.hstglabs.lab;
              import net.bananemdnsa.historystages.api.stage.StageStates;
              class Bad {}
              """);
        assertFalse(BoundaryRules.hsViolations(List.of(source)).isEmpty());
    }

    @Test
    void plantedInternalClassInHsPackageFails() {
        var source = BoundaryRules.planted("hs/Bad.java", """
              package de.germanrangers.hstglabs.hs;
              import net.bananemdnsa.historystages.data.saveddata.StageData;
              class Bad {}
              """);
        assertFalse(BoundaryRules.hsViolations(List.of(source)).isEmpty());
    }

    @Test
    void plantedFullyQualifiedUseWithoutImportFails() {
        var source = BoundaryRules.planted("hs/Bad.java", """
              package de.germanrangers.hstglabs.hs;
              class Bad { Object x = net.bananemdnsa.historystages.data.StageManager.getIndividualStages(); }
              """);
        assertFalse(BoundaryRules.hsViolations(List.of(source)).isEmpty());
    }

    @Test
    void allowlistAndApiPassInHsAndCommand() {
        var hs = BoundaryRules.planted("hs/Ok.java", """
              package de.germanrangers.hstglabs.hs;
              import net.bananemdnsa.historystages.api.stage.StageStates;
              import net.bananemdnsa.historystages.data.StageEntry;
              import net.bananemdnsa.historystages.data.StageMode;
              class Ok {}
              """);
        var command = BoundaryRules.planted("command/Ok.java", """
              package de.germanrangers.hstglabs.command;
              import net.bananemdnsa.historystages.data.DependencyGroup;
              class Ok {}
              """);
        assertEquals(List.of(), BoundaryRules.hsViolations(List.of(hs, command)));
    }

    @Test
    void commentsAndStringsAreIgnored() {
        var source = BoundaryRules.planted("lab/Ok.java", """
              package de.germanrangers.hstglabs.lab;
              // see net.bananemdnsa.historystages.data.StageManager
              class Ok { String s = "net.bananemdnsa.historystages.data.StageManager"; }
              """);
        assertEquals(List.of(), BoundaryRules.hsViolations(List.of(source)));
    }
}
