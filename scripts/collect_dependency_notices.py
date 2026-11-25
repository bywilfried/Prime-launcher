"""Collect original notice bytes without extracting arbitrary archive paths."""
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

OUT = Path("dependency-notices")
OUT.mkdir(exist_ok=True)
rows = []
pattern = re.compile(r"^(LICENSE|LICENCE|NOTICE|COPYING|COPYRIGHT|THIRD[-_ ]PARTY)([._ -].*)?$", re.I)

def scan(z, component, artifact, prefix="", depth=0):
    for entry in z.infolist():
        if entry.is_dir():
            continue
        name = entry.filename
        base = name.rsplit("/", 1)[-1]
        if pattern.match(base) or name.lower() == "assets/licenses.json" or name.lower() == "assets/license.html" or name.lower().startswith("assets/licenses/"):
            row = dict(component=component, artifact=artifact, entry=prefix + name, size=entry.file_size)
            if entry.file_size <= 2 * 1024 * 1024:
                data = z.read(entry)
                digest = hashlib.sha256(data).hexdigest()
                target = OUT / (digest + ".txt")
                target.write_bytes(data)
                row.update(sha256=digest, file=target.name)
            else:
                row["status"] = "size limit exceeded"
            rows.append(row)
        elif depth == 0 and name.lower().endswith(".jar") and entry.file_size <= 64 * 1024 * 1024:
            with zipfile.ZipFile(io.BytesIO(z.read(entry))) as nested:
                scan(nested, component, artifact, prefix + name + "!/", 1)

inputs = json.loads(Path("dependency-notice-input.json").read_text())
inputs.append(dict(component="Prime Launcher APK", file="unsigned-audit/Prime-Launcher-FDroid-unsigned.apk"))
inventory = []
for item in inputs:
    p = Path(item["file"])
    status = dict(component=item["component"], artifact=p.name)
    before = len(rows)
    try:
        if p.suffix.lower() not in (".jar", ".aar", ".apk"):
            status["status"] = "not an archive"
        else:
            with zipfile.ZipFile(p) as z:
                scan(z, item["component"], p.name)
            status["status"] = "scanned"
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        status.update(status="error", error=str(exc))
    status["notices"] = len(rows) - before
    inventory.append(status)
(OUT / "index.json").write_text(json.dumps(dict(artifacts=inventory, notices=rows), indent=2), encoding="utf-8")
print(f"Collected {len(rows)} notice entries from {len(inputs)} artifacts")
if any(x["status"] == "error" for x in inventory):
    raise SystemExit(1)
