package de.germanrangers.hstglabs.guard;

import de.germanrangers.hstglabs.guard.SourceScanner.Source;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The boundary rules of docs/design.md §12.2, as pure functions over scanned sources so each rule
 * can be tested against planted violations as well as the real tree.
 */
final class BoundaryRules {

    static final String HS = "net.bananemdnsa.historystages.";
    static final String HS_API = HS + "api.";
    /** §7.1 rule 1: the only HS classes outside {@code api} we may touch. */
    static final Set<String> HS_ALLOWLIST = Set.of(HS + "data.StageEntry", HS + "data.DependencyGroup", HS + "data.StageMode");
    /** §7.1 rule 2: the only packages that may import HS at all. */
    static final Set<String> HS_PACKAGES = Set.of("hs", "command");

    static final String MEK = "mekanism.";
    static final String MEK_API = "mekanism.api.";
    static final String MEK_PACKAGE = "mek";

    static final List<String> CORE_FORBIDDEN = List.of("net.minecraft.", "net.neoforged.", HS, MEK, "com.mojang.");
    static final String CORE_PACKAGE = "core";

    /** Marker a file derived from Mekanism code must carry (§7.2 rule 4). */
    static final String MEK_DERIVED_MARKER = "Derived from Mekanism";

    private BoundaryRules() {
    }

    /** HS boundary: only api.. plus the allowlist, and only from hs/ and command/. */
    static List<String> hsViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            for (String ref : s.references(HS)) {
                boolean allowedPackage = HS_PACKAGES.stream().anyMatch(s::inPackage);
                if (!allowedPackage) {
                    out.add(SourceScanner.describe(s, "History Stages used outside hs/ and command/: " + ref));
                } else if (!ref.startsWith(HS_API) && !isAllowlisted(ref)) {
                    out.add(SourceScanner.describe(s, "History Stages internal class: " + ref));
                }
            }
        }
        return out;
    }

    private static boolean isAllowlisted(String ref) {
        return HS_ALLOWLIST.stream().anyMatch(allowed -> ref.equals(allowed) || ref.startsWith(allowed + "."));
    }

    /** Core purity: core/ is JDK only. */
    static List<String> coreViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            if (!s.inPackage(CORE_PACKAGE)) {
                continue;
            }
            for (String prefix : CORE_FORBIDDEN) {
                for (String ref : s.references(prefix)) {
                    out.add(SourceScanner.describe(s, "core/ must be pure Java, found " + ref));
                }
            }
        }
        return out;
    }

    /** Mekanism boundary: anything outside mekanism.api.. only inside mek/. */
    static List<String> mekanismViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            if (s.inPackage(MEK_PACKAGE)) {
                continue;
            }
            for (String ref : s.references(MEK)) {
                if (!ref.startsWith(MEK_API)) {
                    out.add(SourceScanner.describe(s, "Mekanism internal used outside mek/: " + ref));
                }
            }
        }
        return out;
    }

    /**
     * Copied or derived Mekanism code must carry the MIT notice and be listed in
     * THIRD_PARTY_NOTICES.md (§7.2 rule 4, §12.2 guardrail 4).
     */
    static List<String> mekanismLicenceViolations(List<Source> sources, String thirdPartyNotices) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            String raw = s.raw();
            boolean derived = raw.contains(MEK_DERIVED_MARKER) || raw.contains("Copyright (c) 2017-2025 Aidan C. Brady");
            if (!derived) {
                continue;
            }
            if (!raw.contains("MIT License") || !raw.contains("Aidan C. Brady")) {
                out.add(SourceScanner.describe(s, "Mekanism-derived file without its MIT notice"));
            }
            String fileName = s.path().getFileName().toString();
            if (!thirdPartyNotices.contains(fileName)) {
                out.add(SourceScanner.describe(s, "Mekanism-derived file not listed in THIRD_PARTY_NOTICES.md"));
            }
        }
        return out;
    }

    static String readNotices() {
        try {
            return Files.readString(SourceScanner.projectDir().resolve("THIRD_PARTY_NOTICES.md"), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    static Source planted(String relativePath, String code) {
        return SourceScanner.parse(Path.of("planted", relativePath), code);
    }
}
