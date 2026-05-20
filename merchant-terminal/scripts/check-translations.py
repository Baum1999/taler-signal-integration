#!/usr/bin/env python3
"""
check-translations.py

Compares Android translation files against the English reference.
Works when run from any directory (auto-detects project root).

Usage:
    python3 check-translations.py                # Overview
    python3 check-translations.py de             # Details for German
    python3 check-translations.py --base /path/to/res
"""

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path


def find_project_res() -> Path | None:
    """Try to auto-detect the res/ folder by walking up from cwd."""
    cwd = Path.cwd().resolve()
    for parent in [cwd] + list(cwd.parents):
        candidate = parent / "merchant-terminal" / "src" / "main" / "res"
        if (candidate / "values" / "strings.xml").exists():
            return candidate
    return None


def get_string_names(xml_path: Path) -> set[str]:
    if not xml_path.exists():
        return set()
    try:
        tree = ET.parse(xml_path)
        names = {
            elem.get("name")
            for elem in tree.findall(".//string")
            if elem.get("name") and elem.get("translatable") != "false"
        }
        return names
    except ET.ParseError:
        return set()


def find_language_files(res_dir: Path) -> dict[str, Path]:
    lang_files = {}
    for values_dir in sorted(res_dir.glob("values-*")):
        strings_file = values_dir / "strings.xml"
        if strings_file.exists():
            lang_code = values_dir.name.removeprefix("values-")
            lang_files[lang_code] = strings_file
    return lang_files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("lang", nargs="?", help="Show missing strings for this language (e.g., de, it)")
    parser.add_argument("--base", help="Explicit path to res/ folder")
    args = parser.parse_args()

    # Determine res directory
    if args.base:
        res_dir = Path(args.base).resolve()
    else:
        res_dir = find_project_res()

    if not res_dir or not (res_dir / "values" / "strings.xml").exists():
        print("Could not find reference strings.xml")
        print("Please run from the project root or use --base /path/to/res")
        return

    reference_file = res_dir / "values" / "strings.xml"
    reference_names = get_string_names(reference_file)

    print(f"Reference: {reference_file}")
    print(f"Translatable strings in reference: {len(reference_names)}\n")

    lang_files = find_language_files(res_dir)

    # Overview
    print("Language     Present   Missing")
    print("-" * 30)
    for lang, fpath in sorted(lang_files.items()):
        present = get_string_names(fpath)
        missing = len(reference_names - present)
        print(f"{lang:<12} {len(present):>7} {missing:>7}")

    print()

    # Detailed view
    if args.lang:
        lang = args.lang
        if lang not in lang_files:
            print(f"Language '{lang}' not found.")
            return

        present = get_string_names(lang_files[lang])
        missing = sorted(reference_names - present)

        print(f"=== Missing strings in '{lang}' ({len(missing)}) ===\n")
        for name in missing:
            print(f"  {name}")


if __name__ == "__main__":
    main()
