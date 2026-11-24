# mekstyle for Claude Code + Blockbench MCP

Copy the contents of this folder into the root of your mod repo (next to build.gradle).

    .claude/skills/mekstyle/SKILL.md          skill Claude Code loads automatically
    .claude/skills/mekstyle/style-guide.yaml  full rule set (source of truth)
    .claude/skills/mekstyle/style-guide.json  same data, for tools without PyYAML
    .claude/skills/mekstyle/scripts/lint_textures.py
    .claude/settings.json                     enables the MCP server, asks before risky_eval
    .mcp.json                                 Blockbench MCP (HTTP, localhost:3000/bb-mcp)
    CLAUDE.md.snippet                         paste into your CLAUDE.md

## Setup
1. Blockbench desktop: File > Plugins > Load Plugin from URL:
   https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js
2. pip install pillow pyyaml
3. Start Blockbench, then `claude` in the repo; check with /mcp that "blockbench" is connected.
   (Alternative without .mcp.json: claude mcp add blockbench --transport http http://localhost:3000/bb-mcp)
4. If you already have .claude/settings.json, merge the two keys instead of overwriting.

## Use
- "Model a basic-tier crusher following mekstyle" -> skill + Blockbench MCP
- "Review assets against mekstyle" -> runs the linter, reports rule IDs
- CI: python .claude/skills/mekstyle/scripts/lint_textures.py src/main/resources/assets/<modid> --strict-palette
  (exit 1 on MUST violations)

Edit style-guide.yaml, then regenerate the JSON:
  python -c "import yaml,json;json.dump(yaml.safe_load(open('.claude/skills/mekstyle/style-guide.yaml')),open('.claude/skills/mekstyle/style-guide.json','w'),indent=2,default=str)"
