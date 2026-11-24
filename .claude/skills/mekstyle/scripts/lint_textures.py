#!/usr/bin/env python3
"""Lint Minecraft textures and block models against the mekstyle guide.

Usage:
  python lint_textures.py <assets_dir> [options]

  <assets_dir>          e.g. src/main/resources/assets/<modid>
  --strict-palette      COL-03: exact-palette check for own-drawn casings (matching --palette-glob)
  --palette-glob GLOB   globs relative to textures/ for --strict-palette (default: block/*casing*)
  --machine-glob GLOB   globs relative to textures/ that must be neutral casings (COL-01)
  --provenance FILE     check art/provenance.yaml entries + shipped license (LEGAL-01/02)
  --repo-root DIR       base for provenance paths (default: current directory)
  --json                machine-readable output

Exit code: 0 = no MUST violations, 1 = MUST violations, 2 = usage error.
Requires Pillow. PyYAML optional (falls back to style-guide.json / provenance.json).
"""
from __future__ import annotations

import argparse
import colorsys
import fnmatch
import json
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow missing: pip install pillow")

HERE = Path(__file__).resolve().parent
GUIDE_YAML = HERE.parent / "style-guide.yaml"
GUIDE_JSON = HERE.parent / "style-guide.json"

NAME_RE = re.compile(r"^[a-z0-9_/]+$")
TEX_REF_RE = re.compile(r"^(#.+|[a-z0-9_.-]+:(block|item)/[a-z0-9_/]+)$")
ALLOWED_ROT = {-45.0, -22.5, 0.0, 22.5, 45.0}
FACE_SIZES = {16, 32}
SHEET_SIZES = {16, 32, 64, 128, 256}
MAX_COLORS = 96
MAX_FRAMES = 32
NEUTRAL_SAT = 0.08
MIN_NEUTRAL = 0.85
MIN_NEUTRAL_ACTIVE = 0.70   # glowing function zone on active/on states
DEFAULT_MACHINE_GLOBS = [
    "block/base/*", "block/*casing*", "block/factory/*",
    "block/*/front*", "block/*/back*", "block/*/side*", "block/*/left*",
    "block/*/right*", "block/*/top*", "block/*/bottom*",
]
TINT_BASE = ("overlay/", "_tint", "mask")
LICENSE_PATHS = ["src/main/resources/licenses/mekanism/LICENSE.txt", "THIRD_PARTY_NOTICES.md"]


def load_structured(yaml_path: Path, json_path: Path):
    if yaml_path.exists():
        try:
            import yaml  # type: ignore
            return yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
        except ImportError:
            pass
    if json_path.exists():
        return json.loads(json_path.read_text(encoding="utf-8"))
    return None


def pixels(img) -> list:
    """RGBA pixel tuples; works on Pillow < 12 and >= 14."""
    fn = getattr(img, "get_flattened_data", None)
    return list(fn() if fn else img.getdata())


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def guide_palette(g: dict) -> set[tuple[int, int, int]]:
    out: set[tuple[int, int, int]] = set()
    c = (g or {}).get("colors", {})
    for s in c.get("steel_ramp", {}).get("swatches", []):
        out.add(hex_to_rgb(s["hex"]))
    if "brass_accent" in c.get("steel_ramp", {}):
        out.add(hex_to_rgb(c["steel_ramp"]["brass_accent"]["hex"]))
    for t in c.get("tiers", {}).get("entries", []):
        out.update(hex_to_rgb(x) for x in t.get("ramp", []))
    for v in c.get("semantic", {}).values():
        if isinstance(v, dict):
            out.update(hex_to_rgb(v[k]) for k in ("hex", "dark") if k in v)
    for v in c.get("status", {}).values():
        out.add(hex_to_rgb(v["hex"]))
    return out


def neutral_share(px: list) -> float:
    opaque = [p for p in px if p[3] > 0]
    if not opaque:
        return 1.0
    n = sum(1 for r, g, b, _ in opaque if colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] <= NEUTRAL_SAT)
    return n / len(opaque)


def matches(rel: str, globs: list[str]) -> bool:
    return any(fnmatch.fnmatch(rel, g) for g in globs)


class Report:
    def __init__(self) -> None:
        self.items: list[dict] = []

    def add(self, level: str, rule: str, path, msg: str) -> None:
        self.items.append({"level": level, "rule": rule, "file": str(path), "message": msg})

    @property
    def must_count(self) -> int:
        return sum(1 for i in self.items if i["level"] == "MUST")


def lint_texture(p: Path, rel: str, rep: Report, a, palette: set) -> None:
    if not NAME_RE.match(rel.removesuffix(".png")):
        rep.add("MUST", "TEX-70", p, "file name not lowercase snake_case")
    try:
        img = Image.open(p).convert("RGBA")
    except Exception as e:  # noqa: BLE001
        rep.add("MUST", "TEX-01", p, f"unreadable PNG: {e}")
        return
    w, h = img.size
    is_gui = rel.startswith("gui/")
    is_sheet = rel.startswith("block/models/") or rel.startswith("item/models/")
    mcmeta = p.with_name(p.name + ".mcmeta")

    if not is_gui:
        allowed = SHEET_SIZES if is_sheet else FACE_SIZES
        if w not in allowed:
            rep.add("MUST", "TEX-01", p, f"width {w} not in {sorted(allowed)}")
        if h % w != 0:
            rep.add("MUST", "ATX-01", p, f"height {h} not a multiple of width {w}")
    frames = h // w if w and h % w == 0 else 1

    anim = None
    if mcmeta.exists():
        try:
            meta = json.loads(mcmeta.read_text(encoding="utf-8"))
        except Exception as e:  # noqa: BLE001
            rep.add("MUST", "ATX-01", p, f"invalid .mcmeta: {e}")
            meta = {}
        if "ctm" in meta:
            rep.add("SHOULD", "TEX-80", p, "mcmeta has CTM data: needs ConnectedTexturesMod dependency or strip it")
        anim = meta.get("animation")
    if frames > 1 and not is_gui and anim is None:
        rep.add("MUST", "ATX-01", p, f"{frames} frames but no animation in .mcmeta")
    if frames > MAX_FRAMES:
        rep.add("SHOULD", "ATX-05", p, f"{frames} frames > {MAX_FRAMES}")
    if anim is not None and frames > 1:
        ft = anim.get("frametime", 1)
        flist = anim.get("frames")
        n = len(flist) if isinstance(flist, list) else frames
        if anim.get("interpolate"):
            rep.add("SHOULD", "ATX-06", p, "interpolate=true: only valid for glow/pulse")
        if ft * n < 7:
            rep.add("SHOULD", "ATX-03", p, f"cycle {ft * n} ticks < 7: check flash area")
        if isinstance(flist, list):
            idx = [f["index"] if isinstance(f, dict) else f for f in flist]
            if idx and max(idx) >= frames:
                rep.add("MUST", "ATX-01", p, f"frames list references index {max(idx)} but strip has {frames}")

    px = pixels(img)
    is_tint_base = any(t in rel for t in TINT_BASE)
    partial = sum(1 for *_, al in px if 0 < al < 255)
    if partial and "translucent" not in rel and "glass" not in rel:
        rep.add("MUST", "TEX-02", p, f"{partial} semi-transparent pixels (only for translucent textures)")
    if not is_tint_base:
        pure = sum(1 for r, g, b, al in px if al > 0 and (r, g, b) in ((0, 0, 0), (255, 255, 255)))
        if pure:
            rep.add("MUST", "COL-30", p, f"{pure} pure black/white pixels")
    if not is_gui and w and h % w == 0:
        for i in range(frames):
            cols = {c[:3] for c in pixels(img.crop((0, i * w, w, (i + 1) * w))) if c[3] > 0}
            limit = MAX_COLORS * (w // 16) ** 2
            if len(cols) > limit:
                rep.add("SHOULD", "COL-31", p, f"frame {i}: {len(cols)} colors > {limit}")
                break
    if rel.startswith("block/overlay/") and neutral_share(px) < 0.95:
        rep.add("MUST", "TEX-50", p, f"overlay not neutral ({neutral_share(px):.0%}); tint at runtime")
    elif not is_sheet and matches(rel, a.machine_glob):
        share = neutral_share(px)
        active = any(t in rel for t in ("_active", "_on"))
        limit = MIN_NEUTRAL_ACTIVE if active else MIN_NEUTRAL
        if share < limit:
            rep.add("SHOULD" if active else "MUST", "COL-01", p,
                    f"{'active face' if active else 'casing'} only {share:.0%} neutral (< {limit:.0%})")
    if a.strict_palette and palette and matches(rel, a.palette_glob):
        off = {c[:3] for c in px if c[3] > 0} - palette
        if off:
            sample = ", ".join("#%02X%02X%02X" % c for c in sorted(off)[:6])
            rep.add("MAY", "COL-03", p, f"{len(off)} off-palette colors (e.g. {sample})")


def lint_model(p: Path, rep: Report) -> None:
    try:
        m = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        rep.add("MUST", "MOD-01", p, f"invalid JSON: {e}")
        return
    for k, v in (m.get("textures") or {}).items():
        if isinstance(v, str) and not TEX_REF_RE.match(v):
            rep.add("MUST", "BB-30", p, f"texture '{k}' -> '{v}' not namespaced")
    elements = m.get("elements") or []
    if elements and "render_type" not in m:
        rep.add("MUST", "MOD-09", p, "custom model without render_type")
    if elements and "display" not in m and "parent" not in m:
        rep.add("MUST", "MOD-11", p, "custom model without display transforms or parent")
    for i, e in enumerate(elements):
        name = e.get("name", f"#{i}")
        if re.match(r"^(cube|group)\d*$", str(name)):
            rep.add("MUST", "BB-01", p, f"element '{name}' has default name")
        for key in ("from", "to"):
            for c in e.get(key, []):
                if (c * 2) % 1 != 0:
                    rep.add("MUST", "MOD-01", p, f"element '{name}' {key} {c} off 0.5 grid")
        rot = e.get("rotation")
        if rot and float(rot.get("angle", 0)) not in ALLOWED_ROT:
            rep.add("MUST", "MOD-04", p, f"element '{name}' rotation {rot.get('angle')}")


def lint_provenance(prov_path: Path, repo: Path, rep: Report) -> None:
    data = load_structured(prov_path, prov_path.with_suffix(".json"))
    if data is None:
        rep.add("MUST", "LEGAL-02", prov_path, "provenance file missing or unreadable")
        return
    entries = data.get("entries", data) if isinstance(data, dict) else data
    needed = ["file", "source_repo", "source_path", "commit", "license", "modified"]
    has_mek = False
    for i, e in enumerate(entries or []):
        miss = [k for k in needed if k not in e]
        if miss:
            rep.add("MUST", "LEGAL-02", prov_path, f"entry {i}: missing {miss}")
            continue
        if not (repo / e["file"]).exists():
            rep.add("MUST", "LEGAL-02", prov_path, f"entry {i}: file not found: {e['file']}")
        if "/assets/mekanism/" in e["file"]:
            rep.add("MUST", "LEGAL-03", e["file"], "imported asset under assets/mekanism/")
        if "mekanism/Mekanism" in str(e["source_repo"]):
            has_mek = True
    if has_mek:
        for lp in LICENSE_PATHS:
            f = repo / lp
            if not f.exists():
                rep.add("MUST", "LEGAL-01", f, "required license/notice file missing")
            elif "Aidan C. Brady" not in f.read_text(encoding="utf-8", errors="replace"):
                rep.add("MUST", "LEGAL-01", f, "upstream copyright line missing")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("assets_dir", type=Path)
    ap.add_argument("--strict-palette", action="store_true")
    ap.add_argument("--palette-glob", nargs="*", default=["block/*casing*"])
    ap.add_argument("--machine-glob", nargs="*", default=DEFAULT_MACHINE_GLOBS)
    ap.add_argument("--provenance", type=Path)
    ap.add_argument("--repo-root", type=Path, default=Path.cwd())
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    root: Path = a.assets_dir
    if not root.is_dir():
        print(f"not a directory: {root}", file=sys.stderr)
        return 2

    rep = Report()
    if root.name == "mekanism":
        rep.add("MUST", "LEGAL-03", root, "linting assets/mekanism: imported assets belong in your own namespace")
    palette = guide_palette(load_structured(GUIDE_YAML, GUIDE_JSON)) if a.strict_palette else set()
    tex_root = root / "textures"
    for p in sorted(tex_root.rglob("*.png")) if tex_root.exists() else []:
        lint_texture(p, p.relative_to(tex_root).as_posix(), rep, a, palette)
    model_root = root / "models"
    for p in sorted(model_root.rglob("*.json")) if model_root.exists() else []:
        lint_model(p, rep)
    if a.provenance:
        lint_provenance(a.provenance, a.repo_root, rep)

    if a.json:
        print(json.dumps({"must": rep.must_count, "issues": rep.items}, indent=2))
    else:
        for i in rep.items:
            print(f"[{i['level']:6}] {i['rule']:8} {i['file']}: {i['message']}")
        print(f"\n{len(rep.items)} issue(s), {rep.must_count} MUST")
    return 1 if rep.must_count else 0


if __name__ == "__main__":
    sys.exit(main())
