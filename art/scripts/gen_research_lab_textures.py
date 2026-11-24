"""Generate the derived/own textures for hstg_research:research_lab (mekstyle 1.2).

Inputs (imported from Mekanism via import_mekanism.py, see art/provenance.yaml):
  textures/block/research_lab/side.png    (Mekanism energized_smelter/side.png)
  textures/block/overlay/items_overlay.png (Mekanism, port frame for the data overlay)

Outputs:
  research_lab/front.png          derived from side.png + own screen (modified: true)
  research_lab/front_active.png   12-frame strip, mek_active_face preset (pingpong)
  overlay/data_overlay.png        Mekanism port frame + own data icon (modified: true)

The front keeps the imported casing ring and the row-9 panel seam so it matches
the other faces (TEX-20, TEX-22); only the upper panel becomes the screen.

Run:  python art/scripts/gen_research_lab_textures.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/hstg_research/textures/block"
PROV = ROOT / "art/provenance.yaml"
MEK = "https://github.com/mekanism/Mekanism"

# steel_ramp (neutral, measured from Mekanism)
S0, S1, S2, S3, S6 = "#161616", "#252525", "#2C2C2C", "#3A3A3A", "#6A6A6A"
# elite accent ramp for the screen glow, E2 == status.active
E0, E2, E3 = "#156A70", "#4BF8FF", "#B0FCFF"
IDLE = "#5A6470"                  # status.idle


# screen: bezel cols 1..14 rows 2..8, glass cols 2..13 rows 3..7
GLASS = (2, 3, 13, 7)
LED = (13, 3)
DATA = {3: [(3, 6), (8, 9)], 5: [(3, 4), (6, 10)], 7: [(3, 3), (5, 6), (8, 8), (10, 11)]}
FRAMES = 12


def rgba(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def put(img, x, y, c, oy=0):
    img.putpixel((x, y + oy), rgba(c))


def screen_base(img, oy=0):
    """Recessed bezel (TEX-11: dark top/left, lit bottom/right) + dark glass."""
    for x in range(1, 15):
        put(img, x, 2, S1, oy)
        put(img, x, 8, S6, oy)
    for y in range(2, 9):
        put(img, 1, y, S1, oy)
        put(img, 14, y, S6, oy)
    put(img, 14, 2, S3, oy)
    put(img, 1, 8, S3, oy)
    x0, y0, x1, y1 = GLASS
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, S0, oy)


def front(side):
    img = side.copy()
    screen_base(img)
    for x, y in ((3, 4), (4, 4), (3, 5)):
        put(img, x, y, S2)            # glass glint, top-left
    put(img, *LED, IDLE)
    return img


def front_active(side):
    """Scan line sweeps cols 2..13; pingpong frames list plays it back (ATX-09)."""
    img = Image.new("RGBA", (16, 16 * FRAMES))
    for f in range(FRAMES):
        oy = f * 16
        img.paste(side, (0, oy))
        screen_base(img, oy)
        cells = set()
        for y, spans in DATA.items():
            for a, b in spans:
                for x in range(a, b + 1):
                    put(img, x, y, E0, oy)
                    cells.add((x, y))
        put(img, *LED, E2, oy)
        line = GLASS[0] + f
        for y in range(GLASS[1], GLASS[3] + 1):
            put(img, line, y, E3 if (line, y) in cells else E2, oy)
    return img


def data_overlay(items):
    """Data port overlay: Mekanism's port frame (from items_overlay) with an own
    'data rows' icon so it is told apart by shape, not tint (COL-21). Neutral,
    tinted white at runtime (TEX-50)."""
    img = items.copy()
    dark = img.getpixel((5, 4))          # Mekanism's interior shade
    light = img.getpixel((7, 6))         # Mekanism's icon highlight (read before overpainting)
    for y in range(4, 12):
        for x in range(4, 12):
            img.putpixel((x, y), dark)
    rows = {5: "L.LL.L", 7: "LL.L.L", 9: "L.L.LL"}
    for y, pattern in rows.items():
        for i, ch in enumerate(pattern):
            if ch == "L":
                img.putpixel((5 + i, y), light)
    return img


def update_provenance(derived):
    """Derived files get their own LEGAL-02 entry with modified: true."""
    sys.path.insert(0, str(ROOT / ".claude/skills/mekstyle/scripts"))
    from import_mekanism import load_entries, write_entries  # noqa: E402

    entries = {e["file"]: e for e in load_entries(PROV)}
    for f, (source_file, notes) in derived.items():
        src = entries[(TEX / source_file).relative_to(ROOT).as_posix()]
        rel = f.relative_to(ROOT).as_posix()
        entries[rel] = {
            "file": rel, "source_repo": MEK, "source_path": src["source_path"],
            "commit": src["commit"], "license": "MIT", "modified": True, "notes": notes,
        }
    write_entries(PROV, list(entries.values()))


def main():
    side = Image.open(TEX / "research_lab/side.png").convert("RGBA")
    items = Image.open(TEX / "overlay/items_overlay.png").convert("RGBA")
    out = {
        TEX / "research_lab/front.png": front(side),
        TEX / "research_lab/front_active.png": front_active(side),
        TEX / "overlay/data_overlay.png": data_overlay(items),
    }
    for path, img in out.items():
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    frames = list(range(FRAMES)) + list(range(FRAMES - 2, 0, -1))   # 0..11..1
    meta = {"animation": {"frametime": 1, "interpolate": False, "frames": frames}}
    (TEX / "research_lab/front_active.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    casing_note = "casing from energized_smelter/side; screen, LED and animation are own work"
    update_provenance({
        TEX / "research_lab/front.png": ("research_lab/side.png", casing_note),
        TEX / "research_lab/front_active.png": ("research_lab/side.png", casing_note),
        TEX / "overlay/data_overlay.png": ("overlay/items_overlay.png", "port frame from items_overlay; data icon is own work"),
    })


if __name__ == "__main__":
    main()
