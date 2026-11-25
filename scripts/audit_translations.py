#!/usr/bin/env python3
"""Report Android string resource coverage without changing translations."""
import argparse
import collections
import json
import sys
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
    tokens = re.findall(r"%(?:\d+\$)?[a-zA-Z]", "".join(node.itertext()).replace("%%", ""))
    # Repeating an explicitly indexed argument does not change the argument contract.
    return collections.Counter([t for t in tokens if "$" not in t] + sorted({t for t in tokens if "$" in t}))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--res", type=Path, default=Path("prime/res"))
    parser.add_argument("--json", type=Path)
    parser.add_argument("--check-fr-en", action="store_true", help="Fail on duplicate resources or French format warnings")
    args = parser.parse_args()
    base, duplicates = read(args.res / "values")
    if not base:
        parser.error("No default string resources found")
    base = {k: v for k, v in base.items() if v.get("translatable") != "false"}
    report = {"default_count": len(base), "default_duplicates": duplicates, "locales": {}}
    resources = {}
    for directory in sorted(args.res.glob("values-*")):
        # Exclude non-language configurations (night, land, API levels, etc.).
        if directory.name == "values-car":
            continue
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
    issues = []
    if duplicates:
        issues.append("Default duplicate resources: " + ", ".join(duplicates))
    for locale in ("values-fr", "values-fr-rFR", "values-fr-rCA"):
        data = report["locales"].get(locale)
        if data is None:
            if locale == "values-fr":
                issues.append("Missing generic French resources")
            continue
        for field in ("duplicates", "placeholder_warnings"):
            if data[field]:
                issues.append(locale + " " + field + ": " + ", ".join(data[field]))
    report["fr_en_validation_issues"] = issues
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if args.check_fr_en and issues:
        for issue in issues:
            print(issue, file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
