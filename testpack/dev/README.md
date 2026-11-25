# Dev playtest content

Copied into `run/config/` before `./gradlew runClient` and `runServer` (task `copyDevContent`), so a
dev world has something to research before the full testpack (WP-20) exists:

- HS stage `hstg_dev_paper` (global, external), which locks the lectern.
- Technology `hstg_labs:dev_paper_research`: 5 units × 100 ticks, one paper per unit.

Try it: place a Basic Lab, power it, feed it paper, run `/hslabs queue add hstg_labs:dev_paper_research`,
and watch `/hslabs queue list`. When it finishes the lectern unlocks.
