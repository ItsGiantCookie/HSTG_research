package de.germanrangers.hstglabs.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Guardrail 4 (docs/design.md §12.2): Mekanism internals only in mek/, copied code keeps its MIT notice. */
class MekanismBoundaryTest {

    @Test
    void sourceTreeRespectsMekanismBoundary() {
        List<String> violations = BoundaryRules.mekanismViolations(SourceScanner.scan(SourceScanner.mainSources()));
        assertEquals(List.of(), violations, "Mekanism boundary violations");
    }

    @Test
    void sourceTreeDerivedFilesCarryMitNotice() {
        List<String> violations = BoundaryRules.mekanismLicenceViolations(SourceScanner.scan(SourceScanner.mainSources()),
              BoundaryRules.readNotices());
        assertEquals(List.of(), violations, "Mekanism licence violations");
    }

    @Test
    void plantedInternalImportOutsideMekFails() {
        var source = BoundaryRules.planted("lab/Bad.java", """
              package de.germanrangers.hstglabs.lab;
              import mekanism.common.tile.base.TileEntityMekanism;
              class Bad {}
              """);
        assertFalse(BoundaryRules.mekanismViolations(List.of(source)).isEmpty());
    }

    @Test
    void guiClassesInMekClientAndApiEverywherePass() {
        var gui = BoundaryRules.planted("mek/client/Ok.java", """
              package de.germanrangers.hstglabs.mek.client;
              import mekanism.client.gui.GuiMekanism;
              import mekanism.client.gui.element.gauge.GuiChemicalGauge;
              class Ok {}
              """);
        var lab = BoundaryRules.planted("lab/Ok.java", """
              package de.germanrangers.hstglabs.lab;
              import mekanism.api.Action;
              class Ok {}
              """);
        assertEquals(List.of(), BoundaryRules.mekanismViolations(List.of(gui, lab)));
    }

    @Test
    void commonInternalsFailEvenInMekD22() {
        for (String path : List.of("mek/Bad.java", "mek/client/Bad.java")) {
            String pkg = path.substring(0, path.lastIndexOf('/')).replace('/', '.');
            var source = BoundaryRules.planted(path, "package de.germanrangers.hstglabs." + pkg + ";\n"
                  + "import mekanism.common.tile.base.TileEntityMekanism;\nclass Bad {}\n");
            assertFalse(BoundaryRules.mekanismViolations(List.of(source)).isEmpty(), path);
        }
    }

    @Test
    void guiClassesOutsideMekClientFail() {
        for (String path : List.of("mek/Bad.java", "client/Bad.java")) {
            String pkg = path.substring(0, path.lastIndexOf('/')).replace('/', '.');
            var source = BoundaryRules.planted(path, "package de.germanrangers.hstglabs." + pkg + ";\n"
                  + "class Bad { Object o = mekanism.client.gui.GuiMekanism.class; }\n");
            assertFalse(BoundaryRules.mekanismViolations(List.of(source)).isEmpty(), path);
        }
    }

    @Test
    void plantedDerivedFileWithoutNoticeFails() {
        var source = BoundaryRules.planted("mek/Copied.java", """
              // Derived from Mekanism GuiUtils
              package de.germanrangers.hstglabs.mek;
              class Copied {}
              """);
        List<String> violations = BoundaryRules.mekanismLicenceViolations(List.of(source), "");
        assertEquals(2, violations.size(), violations.toString()); // no MIT header, not in notices
    }

    @Test
    void derivedFileWithNoticeAndListingPasses() {
        var source = BoundaryRules.planted("mek/Copied.java", """
              /*
               * Derived from Mekanism. MIT License, Copyright (c) 2017-2025 Aidan C. Brady.
               */
              package de.germanrangers.hstglabs.mek;
              class Copied {}
              """);
        assertEquals(List.of(), BoundaryRules.mekanismLicenceViolations(List.of(source), "- `mek/Copied.java`"));
    }
}
