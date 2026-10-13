#!/usr/bin/env python3
"""Render all Prime Launcher legacy mipmaps from the approved SVG, without moving windows."""
from pathlib import Path
import io
import copy
import xml.etree.ElementTree as ET
import cairosvg
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / "docs/prime/prime-logo.svg"
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
root_svg = ET.parse(source).getroot()
svg_ns = "{http://www.w3.org/2000/svg}"
layer = root_svg.find(f".//{svg_ns}g[@id='layer1']")
if layer is None:
    raise RuntimeError("Approved SVG layer not found")
def render_layer(size, hide_id):
    tree = copy.deepcopy(root_svg)
    target = tree.find(f".//{svg_ns}g[@id='layer1']")
    for child in list(target):
        if child.get("id") == hide_id:
            target.remove(child)
    raw = cairosvg.svg2png(bytestring=ET.tostring(tree, encoding="utf-8"), output_width=size, output_height=size)
    return Image.open(io.BytesIO(raw)).convert("RGBA")
for density, size in sizes.items():
    target = root / "res" / f"mipmap-{density}"
    target.mkdir(parents=True, exist_ok=True)
    raw = cairosvg.svg2png(url=str(source), output_width=size, output_height=size)
    image = Image.open(io.BytesIO(raw)).convert("RGBA")
    for name in ("ic_launcher_home.png", "ic_launcher_home_round.png"):
        image.save(target / name, optimize=True)
    render_layer(size, "rect32").save(target / "ic_launcher_home_foreground.png", optimize=True)
    render_layer(size, "g32").save(target / "ic_launcher_home_background.png", optimize=True)
    print(f"Updated {density}: {size}x{size}")
