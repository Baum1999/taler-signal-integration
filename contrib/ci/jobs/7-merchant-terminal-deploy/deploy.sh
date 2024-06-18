#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/merchant-terminal"
APK_PATH="merchant-terminal/build/outputs/apk/release"

# Ensure that keys exist
[[ ! -f "${FDROID_REPO_KEY}" ]] && exit -1
[[ ! -f "${NIGHTLY_KEYSTORE}" ]] && exit -1

set +x
DEBUG_KEYSTORE=$(cat "$FDROID_REPO_KEY")
set -x

# Copy keystore where SDK can find it
cp "${NIGHTLY_KEYSTORE}" /root/.android/debug.keystore

# Rename nightly app
sed -i 's,<string name="app_name">.*</string>,<string name="app_name">Merchant PoS Nightly</string>,' merchant-terminal/src/main/res/values*/strings.xml

# Set time-based version code
export versionCode=$(date '+%s')
sed -i "s,^\(\s*versionCode\) *[0-9].*,\1 $versionCode," merchant-terminal/build.gradle

# Add commit to version name
export versionName=$(git rev-parse --short=7 HEAD)
sed -i "s,^\(\s*versionName\ *\"[0-9].*\)\",\1 ($versionName)\"," merchant-terminal/build.gradle

# Set nightly application ID
sed -i "s,^\(\s*applicationId\) \"*[a-z\.].*\",\1 \"net.taler.merchantpos.nightly\"," merchant-terminal/build.gradle

# Build the APK
./gradlew :merchant-terminal:assembleRelease

# Copy the APK to artifacts folder
mkdir -p "${ARTIFACT_PATH}"
cp "${APK_PATH}"/*.apk "${ARTIFACT_PATH}"

# Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
cp "${APK_PATH}"/*.apk merchant-terminal-debug.apk

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
export CI_PROJECT_URL="https://gitlab.com/gnu-taler/fdroid-repo"
export CI_PROJECT_PATH="gnu-taler/fdroid-repo"
fdroid nightly -v --archive-older 6
