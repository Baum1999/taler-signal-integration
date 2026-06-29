#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="${1:-$REPO_ROOT/merchant-terminal/build/screenshots}"
shift || true

if [[ $# -gt 0 ]]; then
  SCENARIOS=("$@")
else
  SCENARIOS=("login" "mfa-select" "mfa-code" "amount-entry" "order" "order-custom" "order-custom-added" "payment" "payment-success" "history" "refund" "refund-qr" "navigation")
fi

APP_ID="net.taler.merchantpos"
ACTIVITY="$APP_ID/.debug.ScreenshotActivity"
EXTRA_KEY="screenshot_scenario"
LOGCAT_TAG="SCREENSHOT_READY"
WAIT_TIMEOUT=30
BOOTED_EMULATOR=""

mkdir -p "$OUT_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 1
fi

find_emulator_cmd() {
  if command -v emulator >/dev/null 2>&1; then
    echo "emulator"
  elif [[ -x "${ANDROID_HOME:-}/emulator/emulator" ]]; then
    echo "${ANDROID_HOME}/emulator/emulator"
  elif [[ -x "${ANDROID_SDK_ROOT:-}/emulator/emulator" ]]; then
    echo "${ANDROID_SDK_ROOT}/emulator/emulator"
  else
    return 1
  fi
}

shutdown_emulator() {
  if [[ -n "$BOOTED_EMULATOR" ]]; then
    echo "Shutting down emulator ($BOOTED_EMULATOR)..."
    adb -s "$BOOTED_EMULATOR" emu kill 2>/dev/null || true
    BOOTED_EMULATOR=""
  fi
}

trap shutdown_emulator EXIT

CONNECTED_DEVICES=()
while IFS= read -r serial; do
  CONNECTED_DEVICES+=("$serial")
done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if [[ ${#CONNECTED_DEVICES[@]} -eq 0 ]]; then
  EMU_CMD="$(find_emulator_cmd)" || {
    echo "No device connected and emulator command not found. Set ANDROID_HOME or add emulator to PATH." >&2
    exit 1
  }

  AVDS=()
  while IFS= read -r avd; do
    [[ -n "$avd" ]] && AVDS+=("$avd")
  done < <("$EMU_CMD" -list-avds 2>/dev/null)

  if [[ ${#AVDS[@]} -eq 0 ]]; then
    echo "No device connected and no AVDs found." >&2
    exit 1
  fi

  AVD="${AVDS[0]}"
  echo "No device connected. Booting emulator: $AVD"
  "$EMU_CMD" -avd "$AVD" -no-audio -no-boot-anim &>/dev/null &

  echo "Waiting for emulator to boot..."
  adb wait-for-device
  local_elapsed=0
  while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    if [[ $local_elapsed -ge 120 ]]; then
      echo "Emulator boot timed out after 120s" >&2
      exit 1
    fi
    sleep 2
    local_elapsed=$((local_elapsed + 2))
  done
  sleep 1
  echo "Emulator booted."

  while IFS= read -r serial; do
    CONNECTED_DEVICES+=("$serial")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

  BOOTED_EMULATOR="${CONNECTED_DEVICES[0]}"
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB_SERIAL="$ANDROID_SERIAL"
elif [[ ${#CONNECTED_DEVICES[@]} -gt 1 ]]; then
  ADB_SERIAL="${CONNECTED_DEVICES[0]}"
  echo "Multiple devices detected; using $ADB_SERIAL. Set ANDROID_SERIAL to choose another device." >&2
else
  ADB_SERIAL="${CONNECTED_DEVICES[0]}"
fi

if ! printf '%s\n' "${CONNECTED_DEVICES[@]}" | grep -Fxq "$ADB_SERIAL"; then
  echo "Selected Android device '$ADB_SERIAL' is not connected and authorized" >&2
  adb devices -l >&2
  exit 1
fi

export ANDROID_SERIAL="$ADB_SERIAL"
ADB=(adb -s "$ADB_SERIAL")

wait_for_ready() {
  local scenario="$1"
  local elapsed=0
  while [[ $elapsed -lt $WAIT_TIMEOUT ]]; do
    if "${ADB[@]}" logcat -d -s "$LOGCAT_TAG:I" 2>/dev/null | grep -qF "$scenario"; then
      return 0
    fi
    sleep 0.5
    elapsed=$((elapsed + 1))
  done
  echo "  Warning: timed out waiting for ready signal, capturing anyway" >&2
  return 0
}

"$REPO_ROOT/gradlew" :merchant-terminal:installDebug

for scenario in "${SCENARIOS[@]}"; do
  echo "Capturing $scenario"
  "${ADB[@]}" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  "${ADB[@]}" logcat -c 2>/dev/null || true
  "${ADB[@]}" shell am start -W -n "$ACTIVITY" --es "$EXTRA_KEY" "$scenario" >/dev/null
  wait_for_ready "$scenario" "$WAIT_TIMEOUT"
  sleep 3
  "${ADB[@]}" exec-out screencap -p > "$OUT_DIR/$scenario.png"
done

echo "Saved screenshots to $OUT_DIR"
