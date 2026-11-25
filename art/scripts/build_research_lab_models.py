"""Turn the raw Blockbench export into the shipped 1.21.1 NeoForge assets.

Input : art/blockbench/research_lab.export.json  (Blockbench "Java Block/Item" export)
Output: models/block/research_lab.json      (off / idle)
        models/block/research_lab_on.json   (active: animated front face)
        (WP-10: the prototype research_lab block is gone; these two block models are the shared lab
        casing, wired up by gen_basic_lab_assets.py)

Fixes applied to the raw export:
  - namespaced texture refs with readable keys (BB-30)
  - cullface on faces flush with the block boundary (MOD-05)
  - render_type solid (MOD-09), parent block/block for display (MOD-11)
  - drops Blockbench's format_version/groups (not part of the 1.21.1 model format)
  - port overlays are not baked: they render in Configurator view only (client PortOverlayRenderer)
  - active model swaps the front for the animated front_active strip

Run after re-exporting from Blockbench:
  python art/scripts/build_research_lab_models.py
"""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "art/blockbench/research_lab.export.json"
ASSETS = ROOT / "src/main/resources/assets/hstg_labs"
NS = "hstg_labs"

TEXTURES = {  # Blockbench texture name -> (model key, resource path)
    "front": ("front", "block/research_lab/front"),
    "side": ("side", "block/research_lab/side"),
    "back": ("back", "block/research_lab/back"),
    "top": ("top", "block/research_lab/top"),
    "bottom": ("bottom", "block/research_lab/bottom"),
}
FRONT_ACTIVE = "block/research_lab/front_active"

BOUNDARY = {"north": (2, 0, "from"), "south": (2, 16, "to"), "west": (0, 0, "from"),
            "east": (0, 16, "to"), "down": (1, 0, "from"), "up": (1, 16, "to")}



def build_base(raw):
    keymap = {}
    textures = {}
    for idx, name in raw["textures"].items():
        key, path = TEXTURES[name]
        keymap[f"#{idx}"] = f"#{key}"
        textures[key] = f"{NS}:{path}"
    textures["particle"] = textures["side"]

    elements = []
    for el in raw["elements"]:
        el = {k: v for k, v in el.items() if k in ("name", "from", "to", "rotation", "faces", "shade")}
        for face, data in el["faces"].items():
            data["texture"] = keymap[data["texture"]]
            axis, plane, end = BOUNDARY[face]
            if el[end][axis] == plane:
                data["cullface"] = face
        elements.append(el)

    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:solid",
        "textures": textures,
        "elements": elements,
    }


def build_on(base):
    """Mekanism convention: the active state swaps the front face for its animated strip."""
    on = copy.deepcopy(base)
    on["textures"]["front"] = f"{NS}:{FRONT_ACTIVE}"
    return on




def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


def main():
    raw = json.loads(RAW.read_text(encoding="utf-8"))
    base = build_base(raw)
    dump(ASSETS / "models/block/research_lab.json", base)
    dump(ASSETS / "models/block/research_lab_on.json", build_on(base))


if __name__ == "__main__":
    main()
