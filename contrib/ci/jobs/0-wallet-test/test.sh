#!/bin/bash
set -exuo pipefail

./gradlew :wallet:check :wallet:assembleRelease
