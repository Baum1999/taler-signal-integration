#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/cashier"
APK_PATH="cashier/build/outputs/apk/release"

# Ensure that keys exist
[[ ! -f "${FDROID_REPO_KEY}" ]] && exit -1
[[ ! -f "${NIGHTLY_KEYSTORE}" ]] && exit -1

set +x
DEBUG_KEYSTORE=$(cat "$FDROID_REPO_KEY")
set -x

# Copy keystore where SDK can find it
cp "${NIGHTLY_KEYSTORE}" /root/.android/debug.keystore

# Rename nightly app
sed -i 's,<string name="app_name">.*</string>,<string name="app_name">Cashier Nightly</string>,' cashier/src/main/res/values*/strings.xml

# Set time-based version code
export versionCode=$(date '+%s')
sed -i "s,^\(\s*versionCode\) *[0-9].*,\1 $versionCode," cashier/build.gradle

# Set nightly application ID
sed -i "s,^\(\s*applicationId\) \"*[a-z\.].*\",\1 \"net.taler.cashier.nightly\"," cashier/build.gradle

# Test and build the APK
./gradlew :cashier:test :cashier:assembleRelease

# Copy the APK to artifacts folder
mkdir -p "${ARTIFACT_PATH}"
cp "${APK_PATH}"/*.apk "${ARTIFACT_PATH}"

# Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
cp "${APK_PATH}"/*.apk cashier-debug.apk

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
