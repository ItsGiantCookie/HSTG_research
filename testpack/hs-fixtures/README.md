# History Stages fixtures for GameTests

Our own minimal stage files (docs/design.md §14), written from the stage file format in
docs/hs-api-facts.md; nothing here is copied from History Stages. `./gradlew runGameTestServer`
copies them into `run/config/historystages/` first (task `copyHsFixtures`). Every id starts with
`hstg_test_` so they never collide with a pack's stages.
