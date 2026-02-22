#!/bin/bash
set -exuo pipefail

ARTIFACT_PATH="/artifacts/taler-android/${CI_COMMIT_REF}/wallet"
NIGHTLY_APK_PATH="wallet/build/outputs/apk/nightly/release/wallet-nightly-release-unsigned.apk"
NIGHTLY_LINT_PATH="wallet/build/reports/lint-results-fdroidDebug.html"
FDROID_APK_ARCHS=(x86_64 x86 arm64-v8a armeabi-v7a)
FDROID_APK_TEMPLATE="wallet/build/outputs/apk/fdroid/release/wallet-fdroid-%s-release-unsigned.apk"

set +o pipefail
TAG_REGEX='wallet-[0-9.]+(\+.*)?$'
TAG_MATCH="$(git tag --points-at "${CI_COMMIT_REF}" | grep -E "${TAG_REGEX}" | head -1)"
FDROID_VERSION="${TAG_MATCH#wallet-}"
set -o pipefail


# F-Droid nightly build (https://f-droid.org/docs/Publishing_Nightly_Builds/)
function build_nightly_apk {
    [[ ! -f "${NIGHTLY_KEYSTORE_PATH}" ]] && return 1
    echo "Building nightly APK ..."

    # Test and build the APK
    ./gradlew :wallet:check :wallet:assembleNightlyRelease

    # Sign the APK
    apksigner sign \
              --ks "${NIGHTLY_KEYSTORE_PATH}" \
              --ks-key-alias "${NIGHTLY_KEYSTORE_ALIAS}" \
              --ks-pass env:NIGHTLY_KEYSTORE_PASS \
              "${NIGHTLY_APK_PATH}"

    # Copy the APK and lint report to artifacts folder
    mkdir -p "${ARTIFACT_PATH}"
    cp "${NIGHTLY_APK_PATH}" "${ARTIFACT_PATH}"/wallet-nightly-debug.apk
    cp "${NIGHTLY_LINT_PATH}" "${ARTIFACT_PATH}"
}


# F-Droid reproducible build (https://f-droid.org/en/docs/Reproducible_Builds/)
# only build if commit contains release tag e.g. wallet-1.4.0+p1
function build_fdroid_apk {
    [[ -z "${FDROID_VERSION}" ]] && return 0
    [[ ! -f "${FDROID_KEYSTORE_PATH}" ]] && return 0
    echo "Building F-Droid APK (${FDROID_VERSION}) ..."

    # Test and build the APK
    ./gradlew -PsplitApk :wallet:assembleRelease

    # Sign the APK for all architectures
    for arch in "${FDROID_APK_ARCHS[@]}"; do
        apksigner sign \
                  --ks "${FDROID_KEYSTORE_PATH}" \
                  --ks-key-alias "${FDROID_KEYSTORE_ALIAS}" \
                  --ks-pass env:FDROID_KEYSTORE_PASS \
                  "$(printf "${FDROID_APK_TEMPLATE}" "${arch}")"
    done
}


function deploy_nightly_apk {
    [[ ! -f "${SCP_SSH_KEY}" ]] && return 0
    [[ ! -f "${NIGHTLY_APK_PATH}" ]] && return 0
    echo "Deploying nightly APK to taler.net/files ..."

    apk_dest="${SCP_SSH_PATH}"/wallet/wallet-nightly-debug-$(date -u +%s).apk
    latest_dest="${SCP_SSH_PATH}"/wallet/wallet-nightly-debug-latest.apk

    # Deploy APK to taler.net/files/wallet
    scp -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${NIGHTLY_APK_PATH}" \
        "${SCP_SSH_HOST}":"${apk_dest}"

    # Create symbolic link to the latest version
    ssh -i "${SCP_SSH_KEY}" \
        -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null \
        "${SCP_SSH_HOST}" \
        ln -sfr "${apk_dest}" "${latest_dest}"
}


function deploy_fdroid_apk {
    [[ -z "${FDROID_VERSION}" ]] && return 0
    [[ ! -f "${SCP_SSH_KEY}" ]] && return 0
    echo "Deploying F-Droid APK (${FDROID_VERSION}) to taler.net/files ..."

    # Deploy APK for all architectures to taler.net/files/wallet
    for arch in "${FDROID_APK_ARCHS[@]}"; do
        apk_path="$(printf "${FDROID_APK_TEMPLATE}" "${arch}")"
        [[ ! -f "${apk_path}" ]] && return 1
        # extract version name and code (encodes arch)
        version_name="$(aapt2 dump badging "${apk_path}" | grep "versionName" | sed -e "s/.*versionName='//" -e "s/' .*//")"
        version_code="$(aapt2 dump badging "${apk_path}" | grep "versionCode" | sed -e "s/.*versionCode='//" -e "s/' .*//")"
        apk_dest="${SCP_SSH_PATH}"/wallet/fdroid/wallet-fdroid-"${version_name}"-"${version_code}".apk
        scp -i "${SCP_SSH_KEY}" \
            -o StrictHostKeyChecking=no \
            -o UserKnownHostsFile=/dev/null \
            "${apk_path}" \
            "${SCP_SSH_HOST}":"${apk_dest}"
    done
}


function deploy_nightly_fdroid {
    [[ ! -f "${FDROID_REPO_KEY}" ]] && return 0
    echo "Deploying APK to F-droid nightly ..."

    # Copy keystore where SDK can find it
    cp "${NIGHTLY_KEYSTORE_PATH}" /root/.android/debug.keystore

    # Rename APK, so fdroid nightly accepts it (looks for *-debug.apk)
    cp "${NIGHTLY_APK_PATH}" wallet-debug.apk

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


# nightly
build_nightly_apk
deploy_nightly_apk
deploy_nightly_fdroid

# f-droid
build_fdroid_apk
deploy_fdroid_apk
