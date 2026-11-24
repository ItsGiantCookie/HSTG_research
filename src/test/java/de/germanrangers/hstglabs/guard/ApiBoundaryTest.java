package de.germanrangers.hstglabs.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guardrail 2 (docs/design.md §9.1, §12.2): the public api names no internal type; compat, devui and
 * demo use nothing of ours outside api; every api type is documented and marked experimental.
 */
class ApiBoundaryTest {

    @Test
    void sourceTreeApiNamesNoInternalType() {
        assertEquals(List.of(), BoundaryRules.apiViolations(SourceScanner.scan(SourceScanner.mainSources())));
    }

    @Test
    void sourceTreeFeaturesUseOnlyApi() {
        assertEquals(List.of(), BoundaryRules.apiClientViolations(SourceScanner.scan(SourceScanner.mainSources())));
    }

    @Test
    void sourceTreeApiTypesAreDocumentedAndMarked() {
        assertEquals(List.of(), BoundaryRules.apiMarkingViolations(SourceScanner.scan(SourceScanner.mainSources())));
    }

    @Test
    void sourceTreeHasAnApiAndADemo() {
        // a green result must mean something: the scanned tree really contains both
        var sources = SourceScanner.scan(SourceScanner.mainSources());
        assertTrue(sources.stream().anyMatch(s -> s.inPackage("api")), "no api sources found");
        assertTrue(sources.stream().anyMatch(s -> s.inPackage("demo")), "no demo sources found");
    }

    @Test
    void plantedInternalTypeInApiFails() {
        var imported = BoundaryRules.planted("api/Bad.java", """
              package de.germanrangers.hstglabs.api;
              import de.germanrangers.hstglabs.core.ResearchQueue;
              /** x */ @org.jetbrains.annotations.ApiStatus.Experimental public interface Bad { ResearchQueue<String> q(); }
              """);
        var qualified = BoundaryRules.planted("api/client/Bad.java", """
              package de.germanrangers.hstglabs.api.client;
              /** x */ @org.jetbrains.annotations.ApiStatus.Experimental public interface Bad { de.germanrangers.hstglabs.lab.ResearchLabBlock b(); }
              """);
        assertEquals(2, BoundaryRules.apiViolations(List.of(imported, qualified)).size());
    }

    @Test
    void apiMayUseApiMinecraftAndMekanismApi() {
        var ok = BoundaryRules.planted("api/Ok.java", """
              package de.germanrangers.hstglabs.api;
              import de.germanrangers.hstglabs.api.lab.ResearchSite;
              import net.minecraft.resources.ResourceLocation;
              import mekanism.api.chemical.ChemicalStack;
              /** x */ @org.jetbrains.annotations.ApiStatus.Experimental public interface Ok {}
              """);
        assertEquals(List.of(), BoundaryRules.apiViolations(List.of(ok)));
    }

    @Test
    void plantedInternalImportInCompatDevuiDemoFails() {
        for (String pkg : List.of("compat.kubejs", "devui", "demo")) {
            var source = BoundaryRules.planted(pkg.replace('.', '/') + "/Bad.java",
                  "package de.germanrangers.hstglabs." + pkg + ";\nimport de.germanrangers.hstglabs.research.ApiRegistries;\nclass Bad {}\n");
            assertFalse(BoundaryRules.apiClientViolations(List.of(source)).isEmpty(), pkg);
        }
    }

    @Test
    void featuresMayUseApiAndOtherPackagesMayUseAnything() {
        var demo = BoundaryRules.planted("demo/Ok.java", """
              package de.germanrangers.hstglabs.demo;
              import de.germanrangers.hstglabs.api.HSLabsAPI;
              class Ok {}
              """);
        var lab = BoundaryRules.planted("lab/Ok.java", """
              package de.germanrangers.hstglabs.lab;
              import de.germanrangers.hstglabs.research.ApiRegistries;
              class Ok {}
              """);
        assertEquals(List.of(), BoundaryRules.apiClientViolations(List.of(demo, lab)));
    }

    @Test
    void apiTypeWithoutJavadocOrMarkFails() {
        var noDoc = BoundaryRules.planted("api/NoDoc.java", """
              package de.germanrangers.hstglabs.api;
              @org.jetbrains.annotations.ApiStatus.Experimental
              public interface NoDoc {}
              """);
        var noMark = BoundaryRules.planted("api/NoMark.java", """
              package de.germanrangers.hstglabs.api;
              /** Documented but not marked. */
              public interface NoMark {}
              """);
        var ok = BoundaryRules.planted("api/Fine.java", """
              package de.germanrangers.hstglabs.api;
              import org.jetbrains.annotations.ApiStatus;
              /** Documented and marked. */
              @ApiStatus.Experimental
              public record Fine(int a) {}
              """);
        var packageInfo = BoundaryRules.planted("api/package-info.java", "/** docs */\npackage de.germanrangers.hstglabs.api;\n");
        assertEquals(2, BoundaryRules.apiMarkingViolations(List.of(noDoc, noMark, ok, packageInfo)).size());
    }
}
