# Changelog

All notable changes to HSTG: Research Labs. API changes (`de.germanrangers.hstglabs.api..`) are
always listed here (docs/design.md §9.1). Every API type is `@ApiStatus.Experimental` until 1.0.

## Unreleased

### API: first skeleton, `HSLabsAPI.API_VERSION` 1, `API_MINOR` 0 (WP-06)

- `api.HSLabsAPI`: mod id and API version constants.
- `api.registry.RegisterIdEvent<T>`: base of every mod-bus `Register…Event`. Fired once during
  common setup (client setup for `api.client`), frozen after. A late `register` throws
  `IllegalStateException`.
- Extension points (docs/design.md §9.2), one registry each:
  - `ingredient`: `IngredientType<T, S>`, `IngredientStorage<S>` (`ITEM`, `FLUID`, `CHEMICAL`),
    `RegisterIngredientTypesEvent`
  - `reward`: `RewardType<T>`, `RewardContext`, `RegisterRewardTypesEvent`
  - `owner`: `OwnerId`, `OwnerType`, `RegisterOwnerTypesEvent`
  - `tech`: `TechSource`, `RegisterTechSourcesEvent`
  - `lab`: `LabRequirementType<T>`, `RegisterLabRequirementTypesEvent`
  - `lab`: `LabTypeSource`, `RegisterLabTypeSourcesEvent`
  - `lab`: `LabStatsProvider`, `LabStats`, `RegisterLabStatsProvidersEvent` (keyed by block entity
    type id)
  - `modifier`: `Modifier`, `ModifierTarget`, `ModifierScope`, `ModifierContext`,
    `RegisterModifiersEvent`
  - `lab`: `IdleReasonType`, `RegisterIdleReasonsEvent`. The seven built-in reasons are registered
    through it.
  - `template`: `TemplateKind<T>`, `RegisterTemplateKindsEvent`
  - `client`: `IngredientRenderer<T>`, `TechNodeDecorator`, `DevPanel`, `RequirementEditor<T>` and
    their `Register…Event`s
- Role capabilities (§9.3): `lab.ResearchCapabilities.CONTROLLER / CONSUMER / WORKER / NODE` with
  `ResearchController`, `IngredientConsumer`, `ResearchWorker`, `GridNode`.
- Site view: `lab.ResearchSite`, `SiteMember`, `LabRole`.
- Game-bus events (§9.4), declared but not fired yet:
  - `event.ResearchEvent.BeforeEnqueue / QueueChanged / Started / BeforeUnitClaim / UnitCompleted / Completed / Blocked`
  - `event.GridEvent.Formed / Changed / Fault`
  - `tech.ResolveCostEvent`
  - `tech.TechDefinitionsReloadedEvent`
- Not yet: `RegisterStructurePatternsEvent` (M5) and the §9.5 facades (`ResearchApi`, `Technologies`,
  `GridApi`, `TechEditor`, `ClientResearch`), which arrive with the features they front.

### Development

- Demo addon (`demo/`): registers one of every extension point using only `api`. Enabled with
  `-Dhstglabs.demo=true`, which the dev and GameTest runs set.
- Guard tests:
  - `api` names no internal type.
  - `compat`, `devui` and `demo` use only `api`.
  - Every `api` type has javadoc and is marked experimental.
  - D22: Mekanism internals only as GUI classes in `mek/client`.
- `core`: `TechCost`, `ResearchProgress`, `ResearchQueue`, `LabCycle`, `Arbiter`, `SiteScheduler`,
  lab requirements, `FreezableRegistry` (WP-04, WP-05, WP-06). Branch coverage on `core` is enforced
  at 90 % or more.
