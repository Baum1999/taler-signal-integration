#!/bin/bash
set -exuo pipefail

./gradlew :merchant-terminal:check :merchant-terminal:assembleRelease
