#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/cashier"
APK_PATH="cashier/build/outputs/apk/release/cashier-release-unsigned.apk"
LINT_PATH="cashier/build/reports/lint-results-debug.html"

export versionCode=$(date '+%s')

function build_apk {
    [[ ! -f "${NIGHTLY_KEYSTORE_PATH}" ]] && return 1
    echo "Building APK ..."

    # Rename nightly app
    sed -i 's,<string name="app_name">.*</string>,<string name="app_name">Cashier Nightly</string>,' cashier/src/main/res/values*/strings.xml

    # Set time-based version code
    sed -i "s,^\(\s*versionCode\) *[0-9].*,\1 $versionCode," cashier/build.gradle

    # Set nightly application ID
    sed -i "s,^\(\s*applicationId\) \"*[a-z\.].*\",\1 \"net.taler.cashier.nightly\"," cashier/build.gradle

    # Test and build the APK
    ./gradlew :cashier:check :cashier:assembleRelease

    # Sign the APK
    jarsigner -keystore "${NIGHTLY_KEYSTORE_PATH}" \
              -storepass "${NIGHTLY_KEYSTORE_PASS}" \
              "${APK_PATH}" "${NIGHTLY_KEYSTORE_ALIAS}"

    # Copy the APK and lint report to artifacts folder
    mkdir -p "${ARTIFACT_PATH}"
    cp "${APK_PATH}" "${ARTIFACT_PATH}"/cashier-debug.apk
    cp "${LINT_PATH}" "${ARTIFACT_PATH}"
}


function deploy_apk {
    [[ ! -f "${SCP_SSH_KEY}" ]] && return 0
    echo "Deploying APK to taler.net/files ..."

    # Deploy APK to taler.net/files/cashier
    scp -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${APK_PATH}" \
        "${SCP_DEST}"/cashier/cashier-nightly-debug-${versionCode}.apk
}


function deploy_fdroid {
    [[ ! -f "${FDROID_REPO_KEY}" ]] && return 0
    echo "Deploying APK to F-droid nightly ..."

    # Copy keystore where SDK can find it
    cp "${NIGHTLY_KEYSTORE}" /root/.android/debug.keystore

    # Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
    cp "${APK_PATH}"/*.apk cashier-debug.apk

    fdroid --version

    set +x
    export DEBUG_KEYSTORE=$(cat "$FDROID_REPO_KEY")
    set -x

    # Deploy APK to nightly repository
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
