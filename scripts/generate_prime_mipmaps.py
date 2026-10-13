#!/usr/bin/env python3
"""Render all Prime Launcher legacy mipmaps from the approved SVG, without moving windows."""
from pathlib import Path
import io
import cairosvg
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / "docs/prime/prime-logo.svg"
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, size in sizes.items():
    target = root / "res" / f"mipmap-{density}"
    target.mkdir(parents=True, exist_ok=True)
    raw = cairosvg.svg2png(url=str(source), output_width=size, output_height=size)
    image = Image.open(io.BytesIO(raw)).convert("RGBA")
    for name in ("ic_launcher_home.png", "ic_launcher_home_round.png"):
        image.save(target / name, optimize=True)
    # Keep the individual layer PNG names in sync for any legacy references.
    for name in ("ic_launcher_home_foreground.png", "ic_launcher_home_background.png"):
        image.save(target / name, optimize=True)
    print(f"Updated {density}: {size}x{size}")
