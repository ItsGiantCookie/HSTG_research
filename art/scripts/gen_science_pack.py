"""Generate the science pack item assets for hstg_labs (WP-07, docs/design.md §8.2; mekstyle).

One item, many packs: every data-driven pack is the same flask, tinted at runtime with the pack's
colour (COL-12, TEX-60: one template + tint, no per-pack painting).

Textures (own work):
  item/science_pack.png        layer0: conical flask, steel stopper, glass (neutral, never tinted)
  item/science_pack_tint.png   layer1: the liquid as a light grayscale tint base (tint index 1);
                               white is allowed here, it carries the tint (COL-30 exception *_tint*)

JSON (generated, never hand-edited):
  models/item/science_pack.json  item/generated with layer0 + layer1

The flask shape differs from the prototype's sample vial on purpose, so a pack never reads as a sample.

Run:  python art/scripts/gen_science_pack.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_data_assets as data  # noqa: E402  (shared palette: steel ramp, glass)

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/hstg_labs"
TEX = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"

# Flask, symmetric around x = 7.5. Each body row: (y, half width); x runs 8-hw .. 7+hw.
STOPPER = {2: "kcck", 3: "kCCk"}          # x 6..9, steel ramp
NECK = range(4, 7)                         # x 6..9: edge, glass, glass, edge
BODY = [(7, 3), (8, 4), (9, 5), (10, 6), (11, 6), (12, 6)]
BASE = (13, 5)                             # bottom edge row
LIQUID_FROM = 9                            # first row of liquid

# tint base: light ramp, darker toward the bottom and the right edge (light from top left)
TINT_TOP, TINT_MID, TINT_LOW, TINT_EDGE = "#FFFFFF", "#E8E8E8", "#CFCFCF", "#B4B4B4"


def layout():
    """{(x, y): role} with roles stopper:k/c/C, e edge, g glass, w highlight, l liquid."""
    cells = {}
    for y, row in STOPPER.items():
        for i, ch in enumerate(row):
            cells[(6 + i, y)] = ch
    for y in NECK:
        cells[(6, y)] = "e"
        cells[(7, y)] = "w" if y == 4 else "g"
        cells[(8, y)] = "g"
        cells[(9, y)] = "e"
    for y, hw in BODY:
        left, right = 8 - hw, 7 + hw
        for x in range(left, right + 1):
            if x in (left, right):
                cells[(x, y)] = "e"
            elif y >= LIQUID_FROM:
                cells[(x, y)] = "l"
            elif x == left + 1:
                cells[(x, y)] = "w"
            else:
                cells[(x, y)] = "g"
    y, hw = BASE
    for x in range(8 - hw, 8 + hw):
        cells[(x, y)] = "e"
    return cells


def flask_base(cells):
    pal = {"k": data.S3, "c": data.S6, "C": data.S5, "e": data.GLASS_EDGE, "g": data.GLASS, "w": data.GLASS_HI, "l": data.GLASS}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), role in cells.items():
        img.putpixel((x, y), data.rgba(pal[role]))
    return img


def flask_tint(cells):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    liquid = [(x, y) for (x, y), role in cells.items() if role == "l"]
    top = min(y for _, y in liquid)
    bottom = max(y for _, y in liquid)
    for x, y in liquid:
        right = max(px for px, py in liquid if py == y)
        if y == top:
            tone = TINT_TOP
        elif x == right:
            tone = TINT_EDGE
        elif y == bottom:
            tone = TINT_LOW
        else:
            tone = TINT_MID
        img.putpixel((x, y), data.rgba(tone))
    return img


def model():
    return {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": "hstg_labs:item/science_pack",
            "layer1": "hstg_labs:item/science_pack_tint",
        },
    }


def main():
    cells = layout()
    for (x, y) in cells:
        assert 0 <= x < 16 and 0 <= y < 16, (x, y)
        assert (15 - x, y) in cells, f"not symmetric at {(x, y)}"
    TEX.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    flask_base(cells).save(TEX / "science_pack.png")
    flask_tint(cells).save(TEX / "science_pack_tint.png")
    (MODELS / "science_pack.json").write_text(json.dumps(model(), indent=2) + "\n", encoding="utf-8")
    print("wrote item/science_pack(.png, _tint.png) and models/item/science_pack.json")


if __name__ == "__main__":
    main()
