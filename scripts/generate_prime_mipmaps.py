#!/usr/bin/env python3
"""Export Prime icons with Inkscape, preserving the approved SVG geometry."""
from pathlib import Path
from tempfile import TemporaryDirectory
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = root / "docs/prime/prime-logo.svg"
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
def export(svg, destination, size):
    subprocess.run([
        "inkscape", str(svg), "--export-type=png",
        f"--export-filename={destination}",
        f"--export-width={size}", f"--export-height={size}",
        "--export-area-page",
    ], check=True)

def isolated_svg(path, remove_id):
    # Do not parse/reserialize the Inkscape SVG: that rewrites namespace
    # prefixes and causes live path effects to move the four windows.
    original = source.read_text(encoding="utf-8")
    if remove_id == "rect32":
        pattern = r'<rect\b(?=[^>]*\bid="rect32")[^>]*/>'
        modified, count = re.subn(pattern, "", original, count=1)
    elif remove_id == "g32":
        begin = re.search(r'<g\b[^>]*\bid="g32"[^>]*>', original)
        if begin is None:
            raise RuntimeError("Mark group not found")
        depth = 1
        end_pos = None
        for tag in re.finditer(r'</?g\b[^>]*>', original[begin.end():]):
            depth += -1 if tag.group().startswith("</") else 1
            if depth == 0:
                end_pos = begin.end() + tag.end()
                break
        if end_pos is None:
            raise RuntimeError("Mark group is not balanced")
        modified = original[:begin.start()] + original[end_pos:]
        count = 1
    else:
        raise ValueError(remove_id)
    if count != 1:
        raise RuntimeError(f"Could not isolate {remove_id}")
    path.write_text(modified, encoding="utf-8")

with TemporaryDirectory() as temp:
    temp = Path(temp)
    foreground_svg = temp / "foreground.svg"
    background_svg = temp / "background.svg"
    isolated_svg(foreground_svg, "rect32")
    isolated_svg(background_svg, "g32")
    for density, size in sizes.items():
        dest = root / "res" / f"mipmap-{density}"
        dest.mkdir(parents=True, exist_ok=True)
        for name in ("ic_launcher_home.png", "ic_launcher_home_round.png"):
            export(source, dest / name, size)
        export(foreground_svg, dest / "ic_launcher_home_foreground.png", size)
        export(background_svg, dest / "ic_launcher_home_background.png", size)
        print(f"Rendered {density} at {size}x{size} using Inkscape")
