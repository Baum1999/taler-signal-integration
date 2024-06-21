#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/wallet"
APK_PATH="wallet/build/outputs/apk/nightly/release"

# Ensure that keys exist
[[ ! -f "${FDROID_REPO_KEY}" ]] && exit -1
[[ ! -f "${NIGHTLY_KEYSTORE}" ]] && exit -1

set +x
DEBUG_KEYSTORE=$(cat "$FDROID_REPO_KEY")
set -x

# Copy keystore where SDK can find it
cp "${NIGHTLY_KEYSTORE}" /root/.android/debug.keystore

# Test and build the APK
./gradlew :wallet:check :wallet:assembleNightlyRelease

# Copy the APK to artifacts folder
mkdir -p "${ARTIFACT_PATH}"
cp "${APK_PATH}"/*.apk "${ARTIFACT_PATH}"

# Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
cp "${APK_PATH}"/*.apk wallet-debug.apk

# Install fdroidserver and dependencies
apt update
apt-get -qy install --no-install-recommends \
        python3-pip \
        openssh-client \
        rsync
python3 -m pip install --upgrade pip wheel setuptools
python3 -m pip install git+https://gitlab.com/fdroid/fdroidserver.git
fdroid --version

# Deploy APK to nightly repository
export DEBUG_KEYSTORE
export CI=
export CI_PROJECT_URL="https://gitlab.com/gnu-taler/fdroid-repo"
export CI_PROJECT_PATH="gnu-taler/fdroid-repo"
export GITLAB_USER_NAME="$(git log -1 --pretty=format:'%an')"
export GITLAB_USER_EMAIL="$(git log -1 --pretty=format:'%ae')"

fdroid nightly -v --archive-older 6
