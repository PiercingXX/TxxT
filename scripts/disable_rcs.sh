#!/usr/bin/env bash
# Disable device RCS so inbound photos fall back to MMS.
#
# Pixel / GrapheneOS registers Verizon Chat + HTTP file-transfer through
# Shannon RCS even when TxxT (SMS/MMS only) holds ROLE_SMS. Senders then use
# RCS and the picture never becomes WAP_PUSH. Voice IMS is a different
# package and must stay enabled.
#
# Called from Gradle after :app:installDebug / installRelease. Safe to run
# with no device attached (exits 0). Never fails the install.

set -u

adb_bin() {
    if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
        printf '%s\n' "${ANDROID_HOME}/platform-tools/adb"
        return
    fi
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi
    return 1
}

ADB="$(adb_bin || true)"
if [[ -z "${ADB}" ]]; then
    echo "disable_rcs: adb not found; skip"
    exit 0
fi

if ! "${ADB}" get-state >/dev/null 2>&1; then
    echo "disable_rcs: no device; skip"
    exit 0
fi

# RCS only. Do not add com.shannon.imsservice (VoLTE / VoWiFi).
RCS_PACKAGES=(
    com.shannon.rcsservice
    com.google.android.ims
)

for pkg in "${RCS_PACKAGES[@]}"; do
    if "${ADB}" shell pm path "${pkg}" >/dev/null 2>&1; then
        echo "disable_rcs: disable-user ${pkg}"
        "${ADB}" shell pm disable-user --user 0 "${pkg}" || true
    fi
done

# Stop advertising Chat even if an OTA re-enables the package.
"${ADB}" shell cmd phone uce set-device-enabled false >/dev/null 2>&1 || true
"${ADB}" shell cmd phone src set-device-enabled false >/dev/null 2>&1 || true

echo "disable_rcs: done"
exit 0
