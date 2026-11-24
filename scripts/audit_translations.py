#!/usr/bin/env python3
"""Report Android string resource coverage without changing translations."""
import argparse
import collections
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

def read(directory):
    entries = {}
    duplicates = []
    for path in sorted(directory.glob("*.xml")):
        for node in ET.parse(path).getroot():
            kind = node.tag
            if kind not in ("string", "plurals", "string-array"):
                continue
            name = node.get("name")
            if not name:
                continue
            key = kind + "/" + name
            if key in entries:
                duplicates.append(key)
            entries[key] = node
    return entries, duplicates

def placeholders(node):
    return collections.Counter(re.findall(r"%(?:\d+\$)?[a-zA-Z]", "".join(node.itertext()).replace("%%", "")))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--res", type=Path, default=Path("prime/res"))
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    base, duplicates = read(args.res / "values")
    if not base:
        parser.error("No default string resources found")
    base = {k: v for k, v in base.items() if v.get("translatable") != "false"}
    report = {"default_count": len(base), "default_duplicates": duplicates, "locales": {}}
    resources = {}
    for directory in sorted(args.res.glob("values-*")):
        # Exclude non-language configurations (night, land, API levels, etc.).
        if not re.fullmatch(r"values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)", directory.name):
            continue
        localized, dup = read(directory)
        if not localized:
            continue
        resources[directory.name] = (localized, dup)
    for locale, (localized, dup) in resources.items():
        parent = locale.split("-r", 1)[0] if "-r" in locale else None
        inherited = resources.get(parent, ({}, []))[0]
        combined = {**inherited, **localized}
        common = base.keys() & localized.keys()
        combined_missing = sorted(base.keys() - combined.keys())
        missing = sorted(base.keys() - localized.keys())
        mismatches = sorted(k for k in common if base[k].tag == "string" and base[k].get("formatted") != "false" and placeholders(base[k]) != placeholders(localized[k]))
        report["locales"][locale] = {
            "generic_parent": parent,
            "generic_plus_region_coverage_percent": round(100 * (len(base) - len(combined_missing)) / len(base), 1),
            "generic_plus_region_missing": combined_missing,
            "direct_coverage_percent": round(100 * len(common) / len(base), 1),
            "missing": missing, "extra": sorted(localized.keys() - base.keys()),
            "placeholder_warnings": mismatches, "duplicates": dup,
        }
    report["coverage_note"] = "Generic plus region is a resource union, not a full Android locale resolution simulation. Sibling regions and BCP47 fallback are not inferred."
    print("Default translatable resources:", len(base))
    for locale, data in report["locales"].items():
        print(f"{locale:22} {data['direct_coverage_percent']:6.1f}%  combined={data['generic_plus_region_coverage_percent']:6.1f}%  missing={len(data['generic_plus_region_missing'])}  format warnings={len(data['placeholder_warnings'])}")
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
