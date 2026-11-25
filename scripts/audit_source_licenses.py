#!/usr/bin/env python3
"""Read-only inventory of license clues in tracked source and asset files."""
import json
import re
import subprocess
from pathlib import Path

patterns = {
    "spdx": r"SPDX-License-Identifier\s*:\s*([^\r\n]+)",
    "gpl": r"GNU (?:Affero |Lesser )?General Public License|\b(?:AGPL|LGPL|GPL)[- ]?[123](?:\.0)?\b",
    "apache": r"Apache License|Apache-2\.0",
    "mit": r"MIT License|Permission is hereby granted, free of charge",
    "bsd": r"Redistribution and use in source and binary forms",
    "ofl": r"SIL Open Font License|OFL-1\.1",
    "creative_commons": r"Creative Commons|CC-BY|CC BY",
    "restricted": r"all rights reserved|non-commercial|noncommercial|proprietary",
}
paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
rows = []
assets = []
unread = []
for name in filter(None, paths):
    p = Path(name)
    if not p.is_file():
        unread.append(name)
        continue
    if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".svg", ".ttf", ".otf", ".ico", ".gif"}:
        assets.append(name)
    raw = p.read_bytes()
    if b"\0" in raw[:8192]:
        continue
    try:
        lines = raw.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        unread.append(name)
        continue
    for i, line in enumerate(lines, 1):
        for kind, pattern in patterns.items():
            if re.search(pattern, line, re.I):
                rows.append({"path": name, "line": i, "kind": kind, "text": line.strip()[:300]})
report = {"matches": rows, "assets": assets, "unread_or_submodule": unread,
          "limitations": "Heuristic text matches, not legal conclusions. Submodule contents and binary license metadata are not scanned."}
Path("source-license-audit.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
print(f"{len(rows)} license clues; {len(assets)} graphic/font assets; {len(unread)} unread paths")
