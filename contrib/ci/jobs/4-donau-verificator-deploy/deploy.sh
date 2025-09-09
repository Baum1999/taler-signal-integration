#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/donau-verificator"
APK_PATH="donau-verificator/build/outputs/apk/nightly/release/donau-verificator-nightly-release-unsigned.apk"
LINT_PATH="donau-verificator/build/reports/lint-results-fdroidDebug.html"


function build_apk {
    [[ ! -f "${NIGHTLY_KEYSTORE_PATH}" ]] && return 1
    echo "Building APK ..."

    # Test and build the APK
    ./gradlew :donau-verificator:check :donau-verificator:assembleNightlyRelease

    # Sign the APK
    apksigner sign \
              --ks "${NIGHTLY_KEYSTORE_PATH}" \
              --ks-key-alias "${NIGHTLY_KEYSTORE_ALIAS}" \
              --ks-pass env:NIGHTLY_KEYSTORE_PASS \
              "${APK_PATH}"

    # Copy the APK and lint report to artifacts folder
    mkdir -p "${ARTIFACT_PATH}"
    cp "${APK_PATH}" "${ARTIFACT_PATH}"/donau-verificator-nightly-debug.apk
    cp "${LINT_PATH}" "${ARTIFACT_PATH}"
}


function deploy_apk {
    [[ ! -f "${SCP_SSH_KEY}" ]] && return 0
    echo "Deploying APK to taler.net/files ..."

    apk_dest="${SCP_SSH_PATH}"/donau-verificator/donau-verificator-nightly-debug-$(date -u +%s).apk
    latest_dest="${SCP_SSH_PATH}"/donau-verificator/donau-verificator-nightly-debug-latest.apk

    # Deploy APK to taler.net/files/donau-verificator
    scp -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${APK_PATH}" \
        "${SCP_SSH_HOST}":"${apk_dest}"

    # Create symbolic link to the latest version
    ssh -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${SCP_SSH_HOST}" \
        ln -sfr "${apk_dest}" "${latest_dest}"
}


function deploy_fdroid {
    [[ ! -f "${FDROID_REPO_KEY}" ]] && return 0
    echo "Deploying APK to F-droid nightly ..."

    # Copy keystore where SDK can find it
    cp "${NIGHTLY_KEYSTORE_PATH}" /root/.android/debug.keystore

    # Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
    cp "${APK_PATH}" donau-verificator-debug.apk

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
deploy_fdroid
