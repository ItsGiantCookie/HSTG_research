"""Generate the basic lab's block assets for hstg_labs (WP-09, docs/design.md §5.2).

Until WP-10 settles the lab screen and the lab's final look, the basic lab reuses the research lab
models built by build_research_lab_models.py (one shared casing, TEX-20). Only the wiring is new:

  blockstates/basic_lab.json                   facing x active, the research_lab models rotated (MOD-08)
  models/item/basic_lab.json                   parent: the off model
  data/hstg_labs/loot_table/blocks/basic_lab.json
  data/minecraft/tags/block/mineable/pickaxe.json   adds hstg_labs:basic_lab

Run:  python art/scripts/gen_basic_lab_assets.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/hstg_labs"
DATA = RES / "data"
BLOCK = "basic_lab"
OFF, ON = "hstg_labs:block/research_lab", "hstg_labs:block/research_lab_on"
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def blockstate():
    variants = {}
    for facing, y in ROTATION.items():
        for active, model in (("false", OFF), ("true", ON)):
            v = {"model": model}
            if y:
                v["y"] = y
            variants[f"active={active},facing={facing}"] = v
    return {"variants": variants}


def loot_table():
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"hstg_labs:{BLOCK}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"hstg_labs:blocks/{BLOCK}",
    }


def main():
    write(ASSETS / f"blockstates/{BLOCK}.json", blockstate())
    write(ASSETS / f"models/item/{BLOCK}.json", {"parent": OFF})
    write(DATA / f"hstg_labs/loot_table/blocks/{BLOCK}.json", loot_table())
    tag_path = DATA / "minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(tag_path.read_text(encoding="utf-8"))
    if f"hstg_labs:{BLOCK}" not in tag["values"]:
        tag["values"].insert(0, f"hstg_labs:{BLOCK}")
    write(tag_path, tag)
    print(f"wrote {BLOCK} blockstate, item model, loot table, mineable tag")


if __name__ == "__main__":
    main()
