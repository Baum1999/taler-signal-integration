#!/usr/bin/env python3
"""
check-translations.py

Finds all Android string resources by first locating the git root (.git folder),
allowing to run it reliably from sub folders.

Usage:
    python3 check-translations.py           # Overview of all languages
    python3 check-translations.py de        # Missing DE strings per module
    python3 check-translations.py de --lint # + format-arg / array lint checks
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


PLACEHOLDER_RE = re.compile(r"%(?:(\d+)\$)?([%sdf])")


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
            if elem.get("name") and elem.get("translatable") != "false"
        }
    except ET.ParseError:
        return set()


def get_string_map(xml_path: Path) -> dict[str, str]:
    """name -> text content (translatable strings only)."""
    if not xml_path.exists():
        return {}
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return {}
    out: dict[str, str] = {}
    for elem in root.findall(".//string"):
        name = elem.get("name")
        if not name or elem.get("translatable") == "false":
            continue
        out[name] = "".join(elem.itertext())
    return out


def get_string_array_items(xml_path: Path) -> dict[str, list[str]]:
    if not xml_path.exists():
        return {}
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return {}
    out: dict[str, list[str]] = {}
    for arr in root.findall(".//string-array"):
        name = arr.get("name")
        if not name:
            continue
        out[name] = ["".join(item.itertext()) for item in arr.findall("item")]
    return out


def normalize_placeholders(text: str) -> list[tuple[int, str]]:
    """Return sorted (index, type) list for Android format placeholders."""
    result: list[tuple[int, str]] = []
    auto = 1
    for idx, typ in PLACEHOLDER_RE.findall(text):
        if typ == "%":
            continue
        if idx:
            result.append((int(idx), typ))
        else:
            result.append((auto, typ))
            auto += 1
    return sorted(result)


def find_all_res_directories(git_root: Path) -> list[Path]:
    """Find all res/ folders inside the git repository."""
    res_dirs = []
    for values_dir in git_root.rglob("values"):
        if (values_dir / "strings.xml").exists():
            # skip build outputs
            if "build" in values_dir.parts:
                continue
            res_dirs.append(values_dir.parent)
    return sorted(set(res_dirs))


def module_name(git_root: Path, res_dir: Path) -> str:
    try:
        return res_dir.relative_to(git_root).parts[0]
    except Exception:
        return str(res_dir)


def lint_module(
    git_root: Path,
    res_dir: Path,
    lang: str,
) -> list[str]:
    """Lint-like checks for one language vs English reference."""
    ref_file = res_dir / "values" / "strings.xml"
    loc_file = res_dir / f"values-{lang}" / "strings.xml"
    if not ref_file.exists() or not loc_file.exists():
        return []

    issues: list[str] = []
    mod = module_name(git_root, res_dir)
    ref_map = get_string_map(ref_file)
    loc_map = get_string_map(loc_file)
    ref_names = set(ref_map)
    loc_names = set(loc_map)

    for name in sorted(ref_names - loc_names):
        issues.append(f"{mod}: MissingTranslation: {name}")

    for name in sorted(loc_names - ref_names):
        issues.append(f"{mod}: ExtraTranslation: {name}")

    for name in sorted(ref_names & loc_names):
        en_ph = normalize_placeholders(ref_map[name])
        tr_ph = normalize_placeholders(loc_map[name])
        if en_ph != tr_ph:
            issues.append(
                f"{mod}: StringFormatCount: {name} "
                f"(en={en_ph} {lang}={tr_ph})"
            )

    ref_arrays = get_string_array_items(ref_file)
    loc_arrays = get_string_array_items(loc_file)
    for aname, ref_items in sorted(ref_arrays.items()):
        if aname not in loc_arrays:
            # not all locales redefine arrays; only check when present
            continue
        loc_items = loc_arrays[aname]
        if len(ref_items) != len(loc_items):
            issues.append(
                f"{mod}: StringArrayLength: {aname} "
                f"(en={len(ref_items)} {lang}={len(loc_items)})"
            )
        # values/labels pairing when both exist in locale
    if (
        "settings_language_values" in loc_arrays
        and "settings_language_labels" in loc_arrays
    ):
        nv = len(loc_arrays["settings_language_values"])
        nl = len(loc_arrays["settings_language_labels"])
        if nv != nl:
            issues.append(
                f"{mod}: StringArrayPair: settings_language_values/"
                f"labels length {nv}!={nl}"
            )

    return issues


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check Android translation coverage and optional lint."
    )
    parser.add_argument(
        "lang",
        nargs="?",
        help="Language code (e.g. de or fr)",
    )
    parser.add_argument(
        "--lint",
        action="store_true",
        help=(
            "Extra checks: format placeholders, extra keys, "
            "string-array lengths (for LANG, or all langs if LANG omitted)"
        ),
    )
    args = parser.parse_args()

    git_root = find_git_root(Path.cwd())
    if not git_root:
        print(
            "Could not find a .git directory. "
            "Please run from inside a git repository."
        )
        return 1

    print(f"Git root: {git_root}\n")

    res_dirs = find_all_res_directories(git_root)
    if not res_dirs:
        print("No Android string resources found in this repository.")
        return 1

    print(f"Found {len(res_dirs)} resource folder(s)\n")

    lang_data: dict[str, list[dict]] = defaultdict(list)

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

            lang_data[lang_code].append(
                {
                    "res_dir": res_dir,
                    "missing_count": len(missing),
                    "missing_names": sorted(missing),
                }
            )

    print("Language   Folders   Strings Missing")
    print("-" * 35)
    for lang in sorted(lang_data.keys()):
        entries = lang_data[lang]
        total_missing = sum(e["missing_count"] for e in entries)
        print(f"{lang:<10} {len(entries):>7}   {total_missing:>6}")

    print()

    exit_code = 0

    if args.lang:
        lang = args.lang
        if lang not in lang_data:
            print(f"No '{lang}' translations found.")
            return 1

        print(f"=== Missing strings in '{lang}' ===\n")

        for entry in lang_data[lang]:
            res_dir = entry["res_dir"]
            missing_names = entry["missing_names"]
            module = module_name(git_root, res_dir)

            print(f"→ {module}  ({entry['missing_count']} missing)")

            if missing_names:
                exit_code = 1
                for name in missing_names:
                    print(f"   {name}")
            else:
                print("   (complete)")
            print()

    if args.lint:
        langs = [args.lang] if args.lang else sorted(lang_data.keys())
        print(f"=== Lint checks ({', '.join(langs)}) ===\n")
        any_issue = False
        for lang in langs:
            lint_issues: list[str] = []
            for res_dir in res_dirs:
                lint_issues.extend(lint_module(git_root, res_dir, lang))
            if not lint_issues:
                print(f"→ {lang}: clean")
            else:
                any_issue = True
                exit_code = 1
                print(f"→ {lang}: {len(lint_issues)} issue(s)")
                for line in lint_issues:
                    print(f"   {line}")
            print()
        if not any_issue:
            print("All lint checks passed.")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
