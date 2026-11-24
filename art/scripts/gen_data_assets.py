"""Generate the data-network assets for hstg_research (mekstyle 1.2).

Textures (own work unless noted):
  item/research_sample.png       vial with sample, steel cap
  item/empty_sample_vial.png     same silhouette, empty glass
  block/data_cable/<tier>.png    one sheet per tier from ONE accent mask (COL-11, COL-12)
  block/data_consumer/front.png  derived from research_lab/side.png (Mekanism casing) + own screen

JSON (DO-09: generated, never hand-edited):
  models/block/data_cable_{core,arm}.json      templates (6x6 core, arm = one model rotated, MOD-13)
  models/block/<tier>_data_cable_{core,arm}.json, models/item/<tier>_data_cable.json
  blockstates/<tier>_data_cable.json           multipart core + 6 arms
  data_consumer model/blockstate/item, item models, loot tables, mineable tag

Cable sheet layout (16x16, 1 px == 1 u, TEX-03):
  [0,0,6,6]   core faces and arm end caps
  [0,6,6,11]  arm up/down faces   (6 across, 5 along the arm)
  [6,0,11,6]  arm east/west faces (5 along the arm, 6 across)
  Patterns are symmetric along the arm, so face orientation never shows.

Run:  python art/scripts/gen_data_assets.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/hstg_research"
TEX = ASSETS / "textures"
MODID = "hstg_research"

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_research_lab_textures as lab  # noqa: E402  (screen helpers + provenance writer)

# steel_ramp
S0, S1, S2, S3, S4, S5, S6 = "#161616", "#252525", "#2C2C2C", "#3A3A3A", "#484848", "#545454", "#6A6A6A"
# semantic.data: light tones on a neutral body, dark = semantic.data.dark
DATA_LIGHT, DATA_BODY, DATA_SHADE, DATA_DARK = "#E6E6E6", "#D2D2D2", "#B4B4B4", "#6E6E6E"
# colors.tiers ramps (dark, mid, base, light)
TIERS = {
    "basic":    ["#1F6B4C", "#2FA874", "#5FFFB8", "#B5FFDD"],
    "advanced": ["#7A2C22", "#C24E3D", "#FF806A", "#FFC2B6"],
    "elite":    ["#156A70", "#22AEB5", "#4BF8FF", "#B0FCFF"],
    "ultimate": ["#6E2A74", "#B24FBC", "#F787FF", "#FCCBFF"],
}
# sample liquid: elite ramp (reads as "research", not as a tier: items carry no tier)
LIQUID = ["#156A70", "#22AEB5", "#4BF8FF", "#B0FCFF"]
GLASS_EDGE, GLASS, GLASS_HI = "#7F8C94", "#C3CDD2", "#E4EAED"


def rgba(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def paint(img, rows, palette, ox=0, oy=0):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((ox + x, oy + y), rgba(palette[ch]))


# --- cable ------------------------------------------------------------------
# E edge, B body, L lit, S shade, a/A tier accent (mid/base). One mask for every tier.
CORE = [
    "EEEEEE",
    "ELLLBE",
    "ELAaSE",
    "ELaaSE",
    "EBSSSE",
    "EEEEEE",
]
ARM_ACROSS = [  # 6 across (columns) x 5 along (rows), ring in the middle row
    "ELLBSE",
    "ELLBSE",
    "aAAAAa",
    "ELLBSE",
    "ELLBSE",
]


def cable_sheet(ramp):
    pal = {"E": DATA_DARK, "B": DATA_BODY, "L": DATA_LIGHT, "S": DATA_SHADE, "a": ramp[1], "A": ramp[2]}
    img = Image.new("RGBA", (16, 16), rgba(DATA_BODY))  # unused area opaque body (TEX-02)
    paint(img, CORE, pal, 0, 0)
    paint(img, ARM_ACROSS, pal, 0, 6)
    along = ["".join(ARM_ACROSS[c][r] for c in range(5)) for r in range(6)]  # transpose -> 5 x 6
    paint(img, along, pal, 6, 0)
    return img


# --- items ------------------------------------------------------------------
VIAL = [  # c/C/k steel cap (lit, base, shadow), e/E glass edge (lit/dark side), g glass, w glint
    "................",
    ".....cccccc.....",
    ".....CCCCCC.....",
    ".....kkkkkk.....",
    "......e..E......",
    ".....ewgggE.....",
    ".....ewgggE.....",
    ".....ewgggE.....",
    ".....eggggE.....",
    ".....eggggE.....",
    ".....eggggE.....",
    ".....eggggE.....",
    ".....eggggE.....",
    ".....eggggE.....",
    "......EEEE......",
    "................",
]
SAMPLE_FILL = {  # rows 8..13 inside the glass (x 6..9); row 8 is the meniscus
    8: "4433",
    9: "3322",
    10: "3222",
    11: "2221",
    12: "2211",
    13: "1111",
}


def vial(filled):
    pal = {"c": S6, "C": S5, "k": S3, "e": GLASS_EDGE, "E": S5, "g": GLASS, "w": GLASS_HI}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paint(img, VIAL, pal)
    if filled:
        tones = {"1": LIQUID[0], "2": LIQUID[1], "3": LIQUID[2], "4": LIQUID[3]}
        for y, row in SAMPLE_FILL.items():
            for i, ch in enumerate(row):
                img.putpixel((6 + i, y), rgba(tones[ch]))
    return img


# --- data consumer ------------------------------------------------------------
def consumer_front(side):
    """Lab-style screen (same bezel, TEX-20) showing data rows draining into a sink bar."""
    img = side.copy()
    lab.screen_base(img)
    rows = {3: [(3, 5), (8, 9)], 5: [(3, 4), (6, 8)]}
    for y, spans in rows.items():
        for a, b in spans:
            for x in range(a, b + 1):
                lab.put(img, x, y, DATA_SHADE)
    for x in range(3, 12):
        lab.put(img, x, 7, DATA_LIGHT)    # sink bar
    lab.put(img, 13, 3, lab.IDLE)         # status LED, same spot as the lab
    return img


# --- gui -----------------------------------------------------------------------
# Data tab icon, 18x18, same language as Mekanism's gui/items.png / energy.png:
# 12x12 glyph, outline #151515, body #515151, recesses #242424 / #2F2F2F. A stack of
# data rows (bits) so it reads apart from the item crate by shape.
DATA_ICON = [
    "..................",
    "..................",
    "..................",
    ".....aaaaaaaa.....",
    "....abbbbbbbba....",
    "....abddddddba....",
    "....aabbbbbbaa....",
    "....abdbdbbdba....",
    "....aabbbbbbaa....",
    "....abdbbdbdba....",
    "....aabbbbbbaa....",
    "....abbdbdbdba....",
    "....abccccccba....",
    "....abbbbbbbba....",
    ".....aaaaaaaa.....",
    "..................",
    "..................",
    "..................",
]


def data_icon():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    paint(img, DATA_ICON, {"a": "#151515", "b": "#515151", "c": "#242424", "d": "#2F2F2F"})
    return img


# --- json ---------------------------------------------------------------------
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def face(uv, cull=None):
    f = {"uv": uv, "texture": "#cable"}
    if cull:
        f["cullface"] = cull
    return f


CORE_UV, ALONG_UD, ALONG_EW = [0, 0, 6, 6], [0, 6, 6, 11], [6, 0, 11, 6]


def arm_element(name, z0, z1, end):
    faces = {
        end: face(CORE_UV, end),
        "up": face(ALONG_UD), "down": face(ALONG_UD),
        "east": face(ALONG_EW), "west": face(ALONG_EW),
    }
    return {"name": name, "from": [5, 5, z0], "to": [11, 11, z1], "faces": faces}


def core_element():
    return {"name": "core", "from": [5, 5, 5], "to": [11, 11, 11],
            "faces": {d: face(CORE_UV) for d in ("north", "east", "south", "west", "up", "down")}}


def cable_json():
    models = ASSETS / "models"
    write_json(models / "block/data_cable_core.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:solid",
        "textures": {"particle": "#cable"}, "elements": [core_element()]})
    write_json(models / "block/data_cable_arm.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:solid",
        "textures": {"particle": "#cable"}, "elements": [arm_element("arm", 0, 5, "north")]})
    write_json(models / "block/data_cable_item.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:solid",
        "textures": {"particle": "#cable"},
        "elements": [core_element(), arm_element("arm_north", 0, 5, "north"), arm_element("arm_south", 11, 16, "south")],
        "display": {
            "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        }})
    arms = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]
    for tier in TIERS:
        name = f"{tier}_data_cable"
        tex = {"cable": f"{MODID}:block/data_cable/{tier}"}
        write_json(models / f"block/{name}_core.json", {"parent": f"{MODID}:block/data_cable_core", "textures": tex})
        write_json(models / f"block/{name}_arm.json", {"parent": f"{MODID}:block/data_cable_arm", "textures": tex})
        write_json(models / f"item/{name}.json", {"parent": f"{MODID}:block/data_cable_item", "textures": tex})
        parts = [{"apply": {"model": f"{MODID}:block/{name}_core"}}]
        for direction, rot in arms:
            parts.append({"when": {direction: "true"}, "apply": {"model": f"{MODID}:block/{name}_arm", **rot}})
        write_json(ASSETS / f"blockstates/{name}.json", {"multipart": parts})


def consumer_json():
    write_json(ASSETS / "models/block/data_consumer.json", {
        "parent": f"{MODID}:block/research_lab",
        "textures": {"front": f"{MODID}:block/data_consumer/front"}})
    write_json(ASSETS / "models/item/data_consumer.json", {"parent": f"{MODID}:block/data_consumer"})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"{MODID}:block/data_consumer"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    write_json(ASSETS / "blockstates/data_consumer.json", {"variants": variants})


def item_json():
    for item in ("research_sample", "empty_sample_vial"):
        write_json(ASSETS / f"models/item/{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{item}"}})


def loot_and_tags():
    blocks = ["research_lab", "data_consumer"] + [f"{t}_data_cable" for t in TIERS]
    for block in blocks:
        write_json(RES / f"data/{MODID}/loot_table/blocks/{block}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:{block}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{MODID}:blocks/{block}"})
    write_json(RES / "data/minecraft/tags/block/mineable/pickaxe.json", {"values": [f"{MODID}:{b}" for b in blocks]})


def main():
    out = {
        TEX / "item/research_sample.png": vial(True),
        TEX / "item/empty_sample_vial.png": vial(False),
        TEX / "gui/data.png": data_icon(),
        TEX / "block/data_consumer/front.png": consumer_front(Image.open(TEX / "block/research_lab/side.png").convert("RGBA")),
    }
    for tier, ramp in TIERS.items():
        out[TEX / f"block/data_cable/{tier}.png"] = cable_sheet(ramp)
    for path, img in out.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    cable_json()
    consumer_json()
    item_json()
    loot_and_tags()
    lab.update_provenance({
        TEX / "block/data_consumer/front.png": ("research_lab/side.png",
                                               "casing from energized_smelter/side; screen, data glyph and LED are own work"),
    })


if __name__ == "__main__":
    main()
