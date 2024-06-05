#!/bin/bash
set -exuo pipefail

./gradlew :cashier:check :cashier:assembleRelease
