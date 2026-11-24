# History Stages API facts

Verified signatures for every row of docs/design.md §7.1. Source: `ref/History-Stages` at tag
`6.2.0-1.21.1` = commit `1a3b8c5290ab7a85499dce033923ceadfc9e7c56` (matches the doc). Paths are
relative to `ref/History-Stages/src/main/java/net/bananemdnsa/historystages/`.

Rule: before calling anything not listed here, find it in `ref/`, add a row, then write the code.

## Differences from docs/design.md §7.1

| Doc says | Source says | Consequence |
| --- | --- | --- |
| `StageSettingsGroup.store(stage, values)` exists but has no save path | `store` exists (`api/settings/StageSettingsGroup.java:98`) and only writes into the in-memory `StageEntry` (`stage.setAddonSettings(...)`) | Unchanged: still no public call that saves the stage file, so D3 (own files) stands |
| `checkRequirements(id, player)` → `RequirementResult` | Returns `@Nullable RequirementResult`, `null` when the stage id is unknown (`api/stage/StageStates.java:209-216`) | `hs` bridge must treat `null` as "unknown stage" |
| `StageEvent.LostForGood` | Not posted from `api/`; posted by internal `data/relock/LostStages.java:42` | Still an api event we may listen to; noted so nobody looks for the post in `StageStates` |

## Version

| Need | Signature | Where |
| --- | --- | --- |
| API generation | `public static final int API_VERSION = 6` | `api/HistoryStagesAPI.java:30` |
| API additions | `public static final int API_MINOR = 2` | `api/HistoryStagesAPI.java:36` |

Both are compile-time constants (inlined by javac); `hs/HsVersion` reads them reflectively to log the
installed values.

## Stages and state (`api/stage/StageStates.java`)

| Need | Signature | Line | Notes |
| --- | --- | --- | --- |
| List stages | `public static List<StageEntry> loadedStages()` | 76 | Read-only snapshot |
| Find one | `public static StageEntry findStage(String stageId)` | 122 | |
| Scope | `public static StageScope scopeOf(String stageId)` | 132 | `StageScope.GLOBAL` / `INDIVIDUAL` (`api/stage/StageScope.java:12-13`) |
| Global unlocked | `public static boolean isUnlockedGlobal(String stageId, ServerLevel level)` | 86 | |
| Individual unlocked | `public static boolean isUnlockedIndividual(String stageId, ServerPlayer player)` | 94 | |
| Either, for a player | `public static boolean isUnlockedFor(String stageId, ServerPlayer player)` | 144 | |
| Display name | `public static String displayNameFor(String stageId, ServerPlayer player)` | 159 | |
| Blocked by logic | `public static boolean isBlocked(String stageId, ServerPlayer player)` | 180 | |
| Lost for good | `public static boolean isLostForGood(String stageId, ServerPlayer player)` | 193 | |
| Other requirements | `public static @Nullable RequirementResult checkRequirements(String stageId, ServerPlayer player)` | 209-210 | Pedestal-deposited items/XP always read as unmet (javadoc 205-207) |
| Unlock global | `public static boolean unlockGlobal(String stageId, ServerLevel level)` | 266 | Refused while logic-blocked / lost / vetoed. Does **not** check dependency groups |
| Unlock global batch | `public static List<String> unlockGlobal(Collection<String> stageIds, ServerLevel level)` | 448 | One announcement for the batch, each stage its own `Unlocked` event |
| Admin unlock | `public static UnlockOutcome forceUnlockGlobal(String stageId, ServerLevel level)` | 285 | `record UnlockOutcome(boolean unlocked, boolean wasBlocked)` at 73. Commands and GameTest setup only |
| Unlock individual | `public static boolean unlockIndividual(String stageId, ServerPlayer player)` | 352 | |
| Unlock individual, maybe offline | `public static boolean unlockIndividual(String stageId, UUID playerUuid, MinecraftServer server)` | 375 | Offline: stage written + `IndividualUnlocked`; sync at next login (javadoc 369-374). Used for team unlocks (§5.10) |
| Unlock individual batch | `public static List<String> unlockIndividual(Collection<String> stageIds, ServerPlayer player)` | 467 | |
| Relock global | `public static boolean relockGlobal(String stageId, ServerLevel level)` | 600 | Commands and GameTest setup only |

## Graph (`api/stage/StageGraph.java`)

| Need | Signature | Line |
| --- | --- | --- |
| Node states | `public enum Node { UNLOCKED, REACHABLE, BLOCKED, LOCKED }` | 32-39 |
| Requires | `public static List<StageRef> requires(StageRef stage)` | 44 |
| Required by | `public static List<StageRef> requiredBy(StageRef stage)` | 52 |
| State for a viewer | `public static Node state(StageRef stage, ServerPlayer player)` | 65 |
| Missing on path | `public static List<StageRef> missingOnPathTo(StageRef target, ServerPlayer player)` | 82 |
| Stage reference | `public record StageRef(String id, StageScope scope)`; `of`, `global`, `individual` | `api/stage/StageRef.java:10, 14, 19, 23` |

## Progress (`api/stage/StageProgress.java`)

| Need | Signature | Line |
| --- | --- | --- |
| Auto-trigger progress | `public static Optional<Triggers> triggers(String stageId, ServerPlayer player)` | 78 |

## Events (game bus, `NeoForge.EVENT_BUS`)

| Event | Signature | Where | Posted at |
| --- | --- | --- | --- |
| Base | `public abstract class StageEvent extends Event`; `getStageId()`, `getDisplayName()`, `getCause()` | `api/stage/StageEvent.java:20, 31, 35, 40` | |
| Unlocked | `StageEvent.Unlocked` | `StageEvent.java:47` | `StageStates.java:308` |
| Locked | `StageEvent.Locked` | `StageEvent.java:56` | `StageStates.java:613` |
| Individual unlocked | `StageEvent.IndividualUnlocked`, `getPlayerUUID()` | `StageEvent.java:68, 76` | `StageStates.java:393, 572` |
| Individual locked | `StageEvent.IndividualLocked`, `getPlayerUUID()` | `StageEvent.java:87, 95` | `StageStates.java:414, 657` |
| Veto | `StageEvent.BeforeUnlock implements ICancellableEvent`; `isGlobal()`, `@Nullable getPlayerUUID()` | `StageEvent.java:114, 124, 130` | `StageStates.java:535` |
| Lost for good | `StageEvent.LostForGood`; `isGlobal()`, `@Nullable getPlayerUUID()` | `StageEvent.java:140, 150, 156` | internal `data/relock/LostStages.java:42` |
| Cause | `public enum StageChangeCause { …, ADDON }` | `api/stage/StageChangeCause.java:10, 38` | |
| Definitions reloaded | `public class StageDefinitionsChangedEvent extends Event` | `api/stage/StageDefinitionsChangedEvent.java:22` | internal `events/StageDefinitionsEventBridge.java:39` |
| Announcement | `public class StageAnnounceEvent extends Event implements ICancellableEvent` | `api/stage/StageAnnounceEvent.java:20` | `StageStates.java:529` |

## Stage data (allowlisted `data` classes)

| Need | Signature | Where |
| --- | --- | --- |
| Entry | `public class StageEntry` | `data/StageEntry.java:33` |
| Mode | `public StageMode getMode()` | `data/StageEntry.java:700` |
| Icon | `public String getIcon()` (empty string if unset) | `data/StageEntry.java:396` |
| Items | `public List<String> getItems()` | `data/StageEntry.java:246` |
| Recipes | `public List<String> getRecipes()` | `data/StageEntry.java:365` |
| Dependency groups | `public List<DependencyGroup> getDependencies()` | `data/StageEntry.java:439` |
| Modes | `public enum StageMode { DEFAULT, AUTO, EXTERNAL, TEMPORARY, CONDITIONAL }` | `data/StageMode.java:20-25` |
| Groups | `public class DependencyGroup` | `data/DependencyGroup.java:17` |

`EXTERNAL`: scroll generated, but the pedestal refuses to research it (`data/StageMode.java:10`).

## Diagnostics, report, config (mod bus, `IModBusEvent`)

| Need | Signature | Where |
| --- | --- | --- |
| Stage checks | `RegisterStageChecksEvent.register(String id, StageCheck check)` | `api/diagnostics/RegisterStageChecksEvent.java:24, 30` |
| Check context | `StageCheckContext.scope()`, `entry()`, `problems()` | `api/diagnostics/StageCheckContext.java:9, 10, 12` |
| Warn | `Problems.warn(String category, String message)` | `api/diagnostics/Problems.java:12` |
| Report section | `RegisterReportSectionsEvent.register(String id, String title, ReportSection section)` | `api/diagnostics/RegisterReportSectionsEvent.java:20, 26` |
| Config section | `RegisterConfigSectionsEvent.register(AddonConfigSection section)` | `api/config/RegisterConfigSectionsEvent.java:30, 32` |
| Settings groups | `RegisterStageSettingsGroupsEvent`; `StageSettingsGroup.store(StageEntry, SettingsValues)` | `api/settings/RegisterStageSettingsGroupsEvent.java:29`; `api/settings/StageSettingsGroup.java:98` |

## Teams (`api/team/Teams.java`)

| Need | Signature | Line |
| --- | --- | --- |
| Teammates (online) | `public static Set<UUID> teammates(ServerPlayer player)` | 21 |
| Teammates (any) | `public static Set<UUID> teammates(UUID player, MinecraftServer server)` | 28 |
| Same team | `public static boolean areTeammates(UUID a, UUID b, MinecraftServer server)` | 35 |
| Shared stage | `public static boolean isSharedWithTeam(String stageId)` | 43 |
| Provider SPI | `public interface TeamProvider { Set<UUID> teammates(MinecraftServer, UUID) }` | `api/team/TeamProvider.java:21, 27` |

No team id in the HS api (confirms D31: key teams by the FTB Teams UUID).

## Client (`api/client/ClientStages.java`)

| Need | Signature | Line |
| --- | --- | --- |
| Unlocked (client) | `isUnlockedGlobal(String)`, `isUnlockedIndividual(String)`, `isUnlocked(String)` | 26, 31, 36 |
| Views | `globalView()`, `individualView()` → `StageStateView` | 43, 48 |
| Display name | `displayName(String stageId)` | 56 |

## Build facts (same tag)

| Fact | Value | Where |
| --- | --- | --- |
| NeoForge | `neo_version=21.1.249` | `gradle.properties` |
| ModDevGradle | `2.0.140` | `build.gradle:3` |
| Lootr | `implementation "curse.maven:lootr-361276:8695784"`, mods.toml `lootr [0.4,)` required | `build.gradle:161`; `src/main/templates/META-INF/neoforge.mods.toml:34-38` |
| KubeJS | `compileOnly` + `localRuntime "dev.latvian.mods:kubejs-neoforge:2101.7.2-build.374"`; JitPack scoped to `com.github.rtyley` | `build.gradle:194-195, 48-57` |
| GameTests | namespace via `neoforge.enabledGameTestNamespaces`, template `empty` | `build.gradle:120-122`; `gametest/AddonApiTests.java` |

## WP-07: technology validation (`hs/HsStageCatalog`)

| Need | Signature | Where | Notes |
| --- | --- | --- | --- |
| Is the stage loaded | `@Nullable public static StageEntry findStage(String stageId)` | `api/stage/StageStates.java:121-122` | `null` for an unknown id; global first, then individual |
| Mode for D10 | `public StageMode getMode()`; `StageMode.EXTERNAL` | `data/StageEntry.java:700`, `data/StageMode.java:23` | allowlisted `data` types |
| Revalidate on HS reload | `public class StageDefinitionsChangedEvent extends Event` | `api/stage/StageDefinitionsChangedEvent.java:22` | Javadoc (`:15-17`): posted from the server tick on a dedicated server, but from the **client tick** on a client connected to a remote server; our listener only reloads when it runs on our server's thread |

## WP-08: research manager (`hs/HistoryStagesBridge`)

| Need | Signature / fact | Where | Notes |
| --- | --- | --- | --- |
| Scope of a stage | `public static StageScope scopeOf(String stageId)` | `api/stage/StageStates.java:132-136` | Returns `null` for an unknown id; global checked first |
| Unlock cause | `StageEvent.getCause()`; `StageChangeCause.ADDON` is "Another mod, through the api. The default when nothing above applies" | `api/stage/StageEvent.java:40`, `api/stage/StageChangeCause.java:37-38` | Our `unlockGlobal` / `unlockIndividual` calls carry `ADDON` without extra work |
| Refusals in `unlockGlobal` | unknown, already unlocked, lost (`LostStages.isLostGlobal`), logic block (`StageLogicGate`), veto (`BeforeUnlock`) | `api/stage/StageStates.java:266-279` | Every refusal returns `false`; we treat all of them as BLOCKED (D32) |
| Offline individual unlock | `unlockIndividual(String, UUID, MinecraftServer)`: online → the player overload; offline → written + synced at next login | `api/stage/StageStates.java:375-378` | Used for team members who are offline (§5.10) |
| Stage files (GameTest fixtures only) | `config/historystages/global/<id>.json` and `individual/<id>.json`; fields `display_name`, `mode` (`"external"`), `share_with_team`; missing lists default to empty | `ConfigHandler.java:17-30`, `data/StageEntry.java:37-40, 149, 246-247` | We never write HS config at runtime; `testpack/hs-fixtures` holds our own minimal files, copied by Gradle before GameTests |
