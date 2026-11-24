# D01 — Name, mod id, package, licence

Status: **Decided** (Hendrik, 2026-11-24) · Needed by: WP-01

## Question
What are the mod's display name, mod id, Java package and licence?

## Options
1. `hstg_labs`, "HSTG: Research Labs", matching the existing "HSTG: Permissions" addon; MIT.
2. Own brand without the HSTG prefix; MIT.
3. Either name, All Rights Reserved.

## Decision
Option 1: mod id `hstg_labs`, display name "HSTG: Research Labs", package
`io.github.itsgiantcookie.hstglabs`, licence MIT.

## Consequences
- The pre-M0 prototype (`hstg_research`, `com.hstg.research`) was renamed in WP-01. Worlds
  created with the prototype lose its blocks; there are no released worlds.
- Open follow-up: ask the History Stages authors whether the "HSTG" prefix is welcome.

## Amendment (Hendrik, 2026-11-24): package and copyright holder
The first package, Maven group and LICENSE holder used an unrelated group's name, added to the docs
by mistake; this project has no association with it. Hendrik chose instead:

- Package and `mod_group_id`: `io.github.itsgiantcookie.hstglabs`. This is the standard Java/Maven
  form for the GitHub account ItsGiantCookie. It cannot collide with a domain someone else owns, and
  Maven Central accepts it should the API ever be published there (D14). The other options offered
  were `dev.giantcookie` and `com.itsgiantcookie`, both of which imply owning a domain.
- LICENSE holder: "Hendrik (ItsGiantCookie)".
- Fixed forward in one commit; older commits on the already-pushed branches keep the old name (no
  history rewrite, no force-push). Mod id, display name and licence are unchanged.
