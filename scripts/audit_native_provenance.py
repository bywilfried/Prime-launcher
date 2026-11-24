import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

dependencies = json.loads(Path("native-dependencies.json").read_text())
report = []
with ZipFile("unsigned-audit/Prime-Launcher-FDroid-unsigned.apk") as apk:
    for entry in sorted(apk.namelist()):
        if not entry.startswith("lib/") or not entry.endswith(".so"):
            continue
        digest = hashlib.sha256(apk.read(entry)).hexdigest()
        candidates = [d for d in dependencies if d["entry"].split("/")[-2:] == entry.split("/")[-2:]]
        exact = [d for d in candidates if d["sha256"] == digest]
        status = "exact" if exact else "name-and-abi-only" if candidates else "unattributed"
        report.append(dict(entry=entry, sha256=digest, status=status, candidates=exact or candidates))
        print(f"{entry}: {status} - " + ", ".join(d["component"] for d in exact or candidates))
Path("native-provenance.json").write_text(json.dumps(report, indent=2) + "\n")
