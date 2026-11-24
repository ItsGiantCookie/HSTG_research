#!/usr/bin/env python3
"""Import Mekanism textures (MIT) into your mod with provenance + license notice.

Usage:
  python import_mekanism.py --modid mymod SRC[:DEST] [SRC[:DEST] ...] [options]

  SRC    path relative to Mekanism's assets/mekanism/textures/, file or folder
         e.g. block/base  block/crusher  block/overlay/energy_overlay.png
  DEST   optional path relative to your assets/<modid>/textures/ (default: same as SRC)

Options:
  --mekanism-dir DIR   existing local Mekanism clone (skips cloning)
  --ref REF            branch/tag/commit to clone (default: 1.21.x)
  --repo-root DIR      your mod repo root (default: current directory)
  --strip-ctm          remove ConnectedTexturesMod 'ctm' blocks from .mcmeta (TEX-80)
  --dry-run            show what would happen

Writes:
  src/main/resources/assets/<modid>/textures/<DEST>   (png + mcmeta)
  art/provenance.yaml                                (one entry per file, LEGAL-02)
  src/main/resources/licenses/mekanism/LICENSE.txt   (ships in the jar, LEGAL-01)
  THIRD_PARTY_NOTICES.md                             (repo notice, LEGAL-01)
Requires git. PyYAML optional (provenance is written as YAML by hand either way).
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_URL = "https://github.com/mekanism/Mekanism"
TEX_IN_REPO = "src/main/resources/assets/mekanism/textures"


def run(cmd: list[str], cwd: Path | None = None) -> str:
    return subprocess.run(cmd, cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def clone(ref: str, dest: Path, paths: list[str]) -> Path:
    run(["git", "clone", "-q", "--depth", "1", "--filter=blob:none", "--sparse", "-b", ref, REPO_URL, str(dest)])
    run(["git", "sparse-checkout", "set", "--no-cone", "/LICENSE", *[f"/{TEX_IN_REPO}/{p}" for p in paths]], cwd=dest)
    return dest


def yaml_scalar(v) -> str:
    if isinstance(v, bool):
        return "true" if v else "false"
    return json.dumps(str(v))  # JSON strings are valid YAML scalars


def load_entries(prov: Path) -> list[dict]:
    if not prov.exists():
        return []
    try:
        import yaml  # type: ignore
        data = yaml.safe_load(prov.read_text(encoding="utf-8")) or {}
    except ImportError:
        sys.exit(f"{prov} exists; install pyyaml to merge entries")
    return data.get("entries", []) if isinstance(data, dict) else data


def write_entries(prov: Path, entries: list[dict]) -> None:
    lines = ["# Provenance of third-party / derived assets (mekstyle LEGAL-02)", "entries:"]
    for e in sorted(entries, key=lambda x: x["file"]):
        lines.append(f"  - file: {yaml_scalar(e['file'])}")
        for k in ("source_repo", "source_path", "commit", "license", "modified", "notes"):
            lines.append(f"    {k}: {yaml_scalar(e.get(k, ''))}")
    prov.parent.mkdir(parents=True, exist_ok=True)
    prov.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_notices(repo: Path, license_text: str, commit: str, dry: bool) -> None:
    lic = repo / "src/main/resources/licenses/mekanism/LICENSE.txt"
    notice = repo / "THIRD_PARTY_NOTICES.md"
    body = (
        "# Third-party notices\n\n"
        "## Mekanism\n\n"
        f"Some textures are copied or derived from Mekanism ({REPO_URL}), commit {commit}.\n"
        "See art/provenance.yaml for the exact files.\n\n"
        "```\n" + license_text.strip() + "\n```\n"
    )
    if dry:
        print(f"would write {lic} and {notice}")
        return
    lic.parent.mkdir(parents=True, exist_ok=True)
    lic.write_text(license_text, encoding="utf-8")
    if notice.exists() and "## Mekanism" in notice.read_text(encoding="utf-8"):
        print(f"{notice} already has a Mekanism section; check the commit line manually")
    elif notice.exists():
        notice.write_text(notice.read_text(encoding="utf-8").rstrip() + "\n\n" + body.split("\n", 2)[2], encoding="utf-8")
    else:
        notice.write_text(body, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("items", nargs="+", metavar="SRC[:DEST]")
    ap.add_argument("--modid", required=True)
    ap.add_argument("--mekanism-dir", type=Path)
    ap.add_argument("--ref", default="1.21.x")
    ap.add_argument("--repo-root", type=Path, default=Path.cwd())
    ap.add_argument("--strip-ctm", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    if a.modid == "mekanism":
        sys.exit("modid must be your own namespace (LEGAL-03)")
    pairs = [(i.split(":", 1) + [i])[:2] if ":" in i else [i, i] for i in a.items]
    pairs = [(s.strip("/"), d.strip("/")) for s, d in pairs]

    tmp = None
    if a.mekanism_dir:
        mek = a.mekanism_dir.resolve()
    else:
        tmp = Path(tempfile.mkdtemp(prefix="mekanism-"))
        print(f"cloning {REPO_URL}@{a.ref} (sparse) ...")
        mek = clone(a.ref, tmp / "src", [s for s, _ in pairs])
    try:
        commit = run(["git", "rev-parse", "HEAD"], cwd=mek)
        license_text = (mek / "LICENSE").read_text(encoding="utf-8")
        if "MIT" not in license_text:
            sys.exit("upstream LICENSE is not MIT anymore; stop and re-check (LEGAL-06)")
        src_root = mek / TEX_IN_REPO
        dst_root = a.repo_root / "src/main/resources/assets" / a.modid / "textures"
        prov = a.repo_root / "art/provenance.yaml"
        entries = {e["file"]: e for e in load_entries(prov)}

        copied = 0
        for src_rel, dst_rel in pairs:
            src = src_root / src_rel
            if not src.exists():
                print(f"skip: not found upstream: {src_rel}", file=sys.stderr)
                continue
            files = [src] if src.is_file() else sorted(f for f in src.rglob("*") if f.is_file())
            for f in files:
                if f.suffix not in (".png", ".mcmeta"):
                    continue
                inner = f.relative_to(src) if src.is_dir() else Path()
                out = (dst_root / dst_rel / inner) if src.is_dir() else (dst_root / dst_rel)
                rel_out = out.relative_to(a.repo_root).as_posix()
                modified = False
                if a.dry_run:
                    print(f"{f.relative_to(mek)} -> {rel_out}")
                else:
                    out.parent.mkdir(parents=True, exist_ok=True)
                    if f.suffix == ".mcmeta" and a.strip_ctm:
                        meta = json.loads(f.read_text(encoding="utf-8"))
                        if "ctm" in meta:
                            meta.pop("ctm")
                            modified = True
                        if not meta:
                            continue  # CTM-only mcmeta: drop it
                        out.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
                    else:
                        shutil.copy2(f, out)
                entries[rel_out] = {
                    "file": rel_out,
                    "source_repo": REPO_URL,
                    "source_path": f.relative_to(mek).as_posix(),
                    "commit": commit,
                    "license": "MIT",
                    "modified": modified,
                    "notes": "ctm stripped" if modified else "",
                }
                copied += 1
            # single png: also take its .mcmeta
            if src.is_file() and src.suffix == ".png":
                m = src.with_name(src.name + ".mcmeta")
                if m.exists():
                    pairs.append((f"{src_rel}.mcmeta", f"{dst_rel}.mcmeta"))

        if a.dry_run:
            print(f"{copied} file(s) would be imported from {commit[:10]}")
        else:
            write_entries(prov, list(entries.values()))
            print(f"imported {copied} file(s) from {commit[:10]}; provenance: {prov}")
        write_notices(a.repo_root, license_text, commit, a.dry_run)
        print("Next: retexture refs in models to " f"{a.modid}:block/..., then run lint_textures.py --provenance art/provenance.yaml")
    finally:
        if tmp:
            shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
