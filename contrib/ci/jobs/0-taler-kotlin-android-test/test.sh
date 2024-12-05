#!/bin/bash
set -xuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/taler-kotlin-android"
UNIT_TEST_PATH="taler-kotlin-android/build/reports/tests/testDebugUnitTest"

./gradlew :taler-kotlin-android:check
ret=$?

mkdir -p "$ARTIFACT_PATH"
cp -r "$UNIT_TEST_PATH" "$ARTIFACT_PATH"

exit $ret
