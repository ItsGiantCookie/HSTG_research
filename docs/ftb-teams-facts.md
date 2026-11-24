# FTB Teams API facts

FTB Teams is an optional dependency (D31): research owners are FTB teams when it is installed. Every
signature `compat/ftbteams` uses, with `path:line`.

- **Source:** `ref/FTB-Teams` at tag `v2101.1.12` = commit `61da411` (read-only, git-ignored), cloned
  with `git clone --depth 1 --branch v2101.1.12 https://github.com/FTBTeam/FTB-Teams.git ref/FTB-Teams`.
- **Paths:** relative to `ref/FTB-Teams/common/src/main/java/dev/ftb/mods/ftbteams/`.
- **Build:** `dev.ftb.mods:ftb-teams-neoforge:2101.1.12` from https://maven.ftb.dev/releases,
  `compileOnly` plus `localRuntime` (dev runs and GameTests). It pulls in FTB Library 2101.1.40 and
  Architectury 13.0.11.
- **Licence:** we never bundle it or copy its code. It is only a dependency.

| Need | Signature | Where | Notes |
| --- | --- | --- | --- |
| Entry point | `public static FTBTeamsAPI.API api()` | `api/FTBTeamsAPI.java:23` | |
| Manager ready | `boolean isManagerLoaded()` | `api/FTBTeamsAPI.java:59` | True on the server once the team manager exists (server about to start) |
| Manager | `TeamManager getManager()` | `api/FTBTeamsAPI.java:67` | Throws if not loaded or on the client |
| A player's team | `Optional<Team> getTeamForPlayerID(UUID uuid)` | `api/TeamManager.java:49` | The party if the player is in one, else their personal team |
| Team by id | `Optional<Team> getTeamByID(UUID teamId)` | `api/TeamManager.java:82` | |
| Effective team id | `UUID getTeamId()` | `api/Team.java:31` | Party id while in a party, else the player's own UUID (javadoc 24-29). This is our owner key |
| Members | `Set<UUID> getMembers()` | `api/Team.java:170` | |
| Party creation (not used, for reference) | `Team createPartyTeam(ServerPlayer, String, @Nullable String, @Nullable Color4I)` | `api/TeamManager.java:130` | Needs a real logged-in player, which is why GameTests use `gametest/TestTeams` |
