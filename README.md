# HSTG: Research Labs

Factorio-style research for [History Stages](https://github.com/Flix100000/History-Stages):
labs consume science packs over time and unlock History Stages stages through its public API.
NeoForge 1.21.1, Java 21. Requires History Stages 6.2+ and Mekanism 10.7.19+; KubeJS optional.

- Design: [docs/design.md](docs/design.md) · agent rules: [CLAUDE.md](CLAUDE.md)
- API facts: [docs/hs-api-facts.md](docs/hs-api-facts.md), [docs/mek-api-facts.md](docs/mek-api-facts.md)
- Decisions: [docs/decisions/](docs/decisions/) · backlog: [docs/backlog.md](docs/backlog.md)

## Build and verify

```
./gradlew build                # compile + jar
./gradlew test                 # unit + guard tests (no Minecraft)
./gradlew runGameTestServer    # headless GameTests, namespace hstg_labs
./gradlew runClient            # manual client checks
```

`./gradlew runServer` needs `eula=true` in `run/eula.txt`, which you set yourself.

Reference sources for development live in `ref/` (git-ignored); the clone commands are in CLAUDE.md.

## Licence

MIT, see [LICENSE](LICENSE). Mekanism-derived assets and code: see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
