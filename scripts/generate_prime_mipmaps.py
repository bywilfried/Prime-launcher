#!/usr/bin/env python3
"""Export Prime icons with Inkscape, preserving the approved SVG geometry."""
from pathlib import Path
from tempfile import TemporaryDirectory
import copy
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = root / "docs/prime/prime-logo.svg"
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ns = "{http://www.w3.org/2000/svg}"

def export(svg, destination, size):
    subprocess.run([
        "inkscape", str(svg), "--export-type=png",
        f"--export-filename={destination}",
        f"--export-width={size}", f"--export-height={size}",
        "--export-area-page",
    ], check=True)

def isolated_svg(path, remove_id):
    tree = ET.parse(source)
    layer = tree.getroot().find(f".//{ns}g[@id='layer1']")
    if layer is None:
        raise RuntimeError("Original logo layer not found")
    removed = False
    for child in list(layer):
        if child.get("id") == remove_id:
            layer.remove(child)
            removed = True
    if not removed:
        raise RuntimeError(f"Missing SVG element: {remove_id}")
    tree.write(path, encoding="utf-8", xml_declaration=True)

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
