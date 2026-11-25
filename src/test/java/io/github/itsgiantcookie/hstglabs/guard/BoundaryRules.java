package io.github.itsgiantcookie.hstglabs.guard;

import io.github.itsgiantcookie.hstglabs.guard.SourceScanner.Source;
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
    /** D22 = hybrid: the only internals allowed are Mekanism's client GUI classes, and only in mek/client. */
    static final String MEK_CLIENT = "mekanism.client.";
    /** D33: the one common type Mekanism's window classes need, allowed in mek/client only. */
    static final String MEK_WINDOW_DATA = "mekanism.common.inventory.container.SelectedWindowData";
    static final String MEK_CLIENT_PACKAGE = "mek.client";

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

    static final String OURS = SourceScanner.BASE + ".";
    static final String OUR_API = OURS + "api";
    /** Feature packages built ON the api (CLAUDE.md "Our public API"): they import nothing of ours outside api. */
    static final List<String> API_CLIENT_PACKAGES = List.of("compat", "devui", "demo");

    /** No public api signature may name an internal type: api.. references nothing of ours outside api. */
    static List<String> apiViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            if (s.inPackage("api")) {
                for (String ref : s.references(OURS)) {
                    if (!isApi(ref)) {
                        out.add(SourceScanner.describe(s, "api names an internal type: " + ref));
                    }
                }
            }
        }
        return out;
    }

    /** compat/, devui/ and demo/ import nothing of ours outside api. */
    static List<String> apiClientViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            if (API_CLIENT_PACKAGES.stream().anyMatch(s::inPackage)) {
                for (String ref : s.references(OURS)) {
                    if (!isApi(ref)) {
                        out.add(SourceScanner.describe(s, s.relativePackage() + " uses ours outside api: " + ref));
                    }
                }
            }
        }
        return out;
    }

    /** §9.1 + CLAUDE.md: every api type has javadoc and is marked {@code @ApiStatus.Experimental} until 1.0. */
    static List<String> apiMarkingViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            if (!s.inPackage("api") || s.path().getFileName().toString().equals("package-info.java")) {
                continue;
            }
            if (!s.raw().contains("/**")) {
                out.add(SourceScanner.describe(s, "api type without javadoc"));
            }
            if (!s.code().contains("ApiStatus.Experimental")) {
                out.add(SourceScanner.describe(s, "api type not marked @ApiStatus.Experimental"));
            }
        }
        return out;
    }

    private static boolean isApi(String ref) {
        return ref.equals(OUR_API) || ref.startsWith(OUR_API + ".");
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

    /**
     * Mekanism boundary (D22 = hybrid, docs/decisions/D22-mekanism-depth.md): mekanism.api.. anywhere;
     * Mekanism's client classes (GuiMekanism, gauges, bars, tabs) only in mek/client; no other internal
     * (tile base, components, registration, attachments) anywhere.
     */
    static List<String> mekanismViolations(List<Source> sources) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) {
            for (String ref : s.references(MEK)) {
                if (ref.startsWith(MEK_API)) {
                    continue;
                }
                boolean windowData = ref.equals(MEK_WINDOW_DATA) || ref.startsWith(MEK_WINDOW_DATA + ".");
                if (!ref.startsWith(MEK_CLIENT) && !windowData) {
                    out.add(SourceScanner.describe(s, "Mekanism internal outside its client GUI classes (D22): " + ref));
                } else if (!s.inPackage(MEK_CLIENT_PACKAGE)) {
                    out.add(SourceScanner.describe(s, "Mekanism GUI class used outside mek/client (D22): " + ref));
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
