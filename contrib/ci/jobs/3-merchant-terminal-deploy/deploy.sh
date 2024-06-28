#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH_LIB="/artifacts/taler-android/${CI_COMMIT_REF}/merchant-lib"
ARTIFACT_PATH_POS="/artifacts/taler-android/${CI_COMMIT_REF}/merchant-terminal"
APK_PATH="merchant-terminal/build/outputs/apk/release/merchant-terminal-release-unsigned.apk"
LINT_PATH_LIB="merchant-lib/build/reports/lint-results-debug.html"
LINT_PATH_POS="merchant-terminal/build/reports/lint-results-debug.html"

export versionCode=$(date '+%s')

function build_apk {
    [[ ! -f "${NIGHTLY_KEYSTORE_PATH}" ]] && return 1
    echo "Building APK ..."

    # Rename nightly app
    sed -i 's,<string name="app_name">.*</string>,<string name="app_name">Merchant PoS Nightly</string>,' merchant-terminal/src/main/res/values*/strings.xml

    # Set time-based version code
    sed -i "s,^\(\s*versionCode\) *[0-9].*,\1 $versionCode," merchant-terminal/build.gradle

    # Add commit to version name
    export versionName=$(git rev-parse --short=7 HEAD)
    sed -i "s,^\(\s*versionName\ *\"[0-9].*\)\",\1 ($versionName)\"," merchant-terminal/build.gradle

    # Set nightly application ID
    sed -i "s,^\(\s*applicationId\) \"*[a-z\.].*\",\1 \"net.taler.merchantpos.nightly\"," merchant-terminal/build.gradle

    # Test and build the APK
    ./gradlew :merchant-lib:check :merchant-terminal:check :merchant-terminal:assembleRelease

    # Sign the APK
    jarsigner -keystore "${NIGHTLY_KEYSTORE_PATH}" \
              -storepass "${NIGHTLY_KEYSTORE_PASS}" \
              "${APK_PATH}" "${NIGHTLY_KEYSTORE_ALIAS}"

    # Copy the APK and lint reports to artifacts folder
    mkdir -p "${ARTIFACT_PATH_POS}"
    mkdir -p "${ARTIFACT_PATH_LIB}"
    cp "${APK_PATH}" "${ARTIFACT_PATH_POS}"/merchant-terminal-debug.apk
    cp "${LINT_PATH_LIB}" "${ARTIFACT_PATH_LIB}"
    cp "${LINT_PATH_POS}" "${ARTIFACT_PATH_POS}"
}


function deploy_apk {
    [[ ! -f "${SCP_SSH_KEY}" ]] && return 0
    echo "Deploying APK to taler.net/files ..."

    # Deploy APK to taler.net/files/pos
    scp -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${APK_PATH}" \
        "${SCP_DEST}"/merchant-terminal/merchant-terminal-nightly-debug-${versionCode}.apk
}


function deploy_fdroid {
    [[ ! -f "${FDROID_REPO_KEY}" ]] && return 0
    echo "Deploying APK to F-droid nightly ..."

    # Copy keystore where SDK can find it
    cp "${NIGHTLY_KEYSTORE_PATH}" /root/.android/debug.keystore

    # Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
    cp "${APK_PATH}"/*.apk merchant-terminal-debug.apk

    fdroid --version

    set +x
    export DEBUG_KEYSTORE=$(cat "$FDROID_REPO_KEY")
    set -x

    # Deploy APK to nightly repository
    export DEBUG_KEYSTORE
    export CI=
    export CI_PROJECT_URL="https://gitlab.com/gnu-taler/fdroid-repo"
    export CI_PROJECT_PATH="gnu-taler/fdroid-repo"
    export GITLAB_USER_NAME="$(git log -1 --pretty=format:'%an')"
    export GITLAB_USER_EMAIL="$(git log -1 --pretty=format:'%ae')"
    fdroid nightly -v --archive-older 6
}

build_apk
deploy_apk
# deploy_fdroid
