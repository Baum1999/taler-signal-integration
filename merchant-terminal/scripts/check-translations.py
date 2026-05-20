#!/usr/bin/env python3
"""
check-translations.py

Finds all Android string resources by first locating the git root (.git folder), allowing to
run it reliably from sub folders.

Usage:
    python3 check-translations.py           # Overview of all languages, across all repo modules
    python3 check-translations.py de        # Show concrete missing German strings per-module
"""

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import defaultdict


def find_git_root(start: Path) -> Path | None:
    """Walk upwards until we find a .git directory."""
    current = start.resolve()
    while current != current.parent:
        if (current / ".git").exists():
            return current
        current = current.parent
    return None


def get_string_names(xml_path: Path) -> set[str]:
    if not xml_path.exists():
        return set()
    try:
        tree = ET.parse(xml_path)
        return {
            elem.get("name")
            for elem in tree.findall(".//string")
            # Only propose string names which are thought to be translated.
            if elem.get("name") and elem.get("translatable") != "false"
        }
    except ET.ParseError:
        return set()


def find_all_res_directories(git_root: Path) -> list[Path]:
    """Find all res/ folders inside the git repository."""
    res_dirs = []
    for values_dir in git_root.rglob("values"):
        if (values_dir / "strings.xml").exists():
            res_dirs.append(values_dir.parent)
    return sorted(set(res_dirs))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("lang", nargs="?", help="Language code (e.g., de or it)")
    args = parser.parse_args()

    git_root = find_git_root(Path.cwd())

    if not git_root:
        print("Could not find a .git directory. Please run from inside a git repository.")
        return

    print(f"Git root: {git_root}\n")

    res_dirs = find_all_res_directories(git_root)

    if not res_dirs:
        print("No Android string resources found in this repository.")
        return

    print(f"Found {len(res_dirs)} resource folder(s)\n")

    lang_data = defaultdict(list)

    for res_dir in res_dirs:
        ref_file = res_dir / "values" / "strings.xml"
        if not ref_file.exists():
            continue

        ref_names = get_string_names(ref_file)
        if not ref_names:
            continue

        for values_dir in res_dir.glob("values-*"):
            lang_code = values_dir.name.removeprefix("values-")
            strings_file = values_dir / "strings.xml"
            if not strings_file.exists():
                continue

            present = get_string_names(strings_file)
            missing = ref_names - present

            lang_data[lang_code].append({
                "res_dir": res_dir,
                "missing_count": len(missing),
                "missing_names": sorted(missing)
            })

    # Overview
    print("Language   Folders   Total Missing")
    print("-" * 35)
    for lang in sorted(lang_data.keys()):
        entries = lang_data[lang]
        total_missing = sum(e["missing_count"] for e in entries)
        print(f"{lang:<10} {len(entries):>7}   {total_missing:>6}")

    print()

    if args.lang:
        lang = args.lang
        if lang not in lang_data:
            print(f"No '{lang}' translations found.")
            return

        print(f"=== Missing strings in '{lang}' ===\n")

        for entry in lang_data[lang]:
            res_dir = entry["res_dir"]
            missing_names = entry["missing_names"]

            # Show actual module names (merchant-terminal, cashier, wallet, ...)
            try:
                module = res_dir.relative_to(git_root).parts[0]
            except Exception:
                module = str(res_dir)

            print(f"→ {module}  ({entry['missing_count']} missing)")

            if missing_names:
                for name in missing_names:
                    print(f"   {name}")
            else:
                print("   (complete)")
            print()


if __name__ == "__main__":
    main()
