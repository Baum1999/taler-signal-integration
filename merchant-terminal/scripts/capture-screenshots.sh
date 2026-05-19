#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="${1:-$REPO_ROOT/merchant-terminal/build/screenshots}"
shift || true

if [[ $# -gt 0 ]]; then
  SCENARIOS=("$@")
else
  SCENARIOS=("amount-entry" "order" "payment" "payment-success")
fi

APP_ID="net.taler.merchantpos"
ACTIVITY="$APP_ID/.MainActivity"
EXTRA_KEY="taler_pos_screenshot_scenario"

mkdir -p "$OUT_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No Android device or emulator detected" >&2
  exit 1
fi

"$REPO_ROOT/gradlew" :merchant-terminal:installDebug

for scenario in "${SCENARIOS[@]}"; do
  echo "Capturing $scenario"
  adb shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb shell am start -W -n "$ACTIVITY" --es "$EXTRA_KEY" "$scenario" >/dev/null
  sleep 2
  adb exec-out screencap -p > "$OUT_DIR/$scenario.png"
done

echo "Saved screenshots to $OUT_DIR"
