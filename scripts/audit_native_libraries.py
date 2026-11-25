#!/usr/bin/env python3
"""Compare APK native entries with resolved runtime archives; no provenance guesses."""
import hashlib
import json
import sys
import zipfile
from pathlib import Path

def entries(path):
    if not zipfile.is_zipfile(path):
        return
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            parts = entry.filename.split("/")
            if len(parts) == 3 and parts[0] in ("jni", "lib") and parts[2].endswith(".so"):
                yield (parts[1], parts[2]), hashlib.sha256(archive.read(entry)).hexdigest()

artifacts = json.loads(Path(sys.argv[1]).read_text())
index = {}
for artifact in artifacts:
    for key, digest in entries(artifact["file"]):
        index.setdefault(key, []).append({
            "component": artifact["component"],
            "archive": Path(artifact["file"]).name,
            "sha256": digest,
        })
report = []
for (abi, name), digest in entries(sys.argv[2]):
    candidates = index.get((abi, name), [])
    exact = [c for c in candidates if c["sha256"] == digest]
    report.append({"abi": abi, "library": name, "sha256": digest,
                   "status": "exact" if exact else "name_only" if candidates else "unattributed",
                   "exact_matches": exact, "candidates": candidates})
Path(sys.argv[3]).write_text(json.dumps(report, indent=2) + "\n")
for row in report:
    print(row["abi"], row["library"], row["status"],
          ", ".join(c["component"] for c in row["candidates"]))
