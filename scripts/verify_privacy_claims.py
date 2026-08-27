#!/usr/bin/env python3
"""Verify the T2 permission claims on the built APK.

The README (T1) states the WS16 posture as fact. This script proves it against
the built artifact, not the source manifest: it builds the debug APK with the
wired toolchain and dumps the APK's actual permission list with
`aapt2 dump permissions`.

The point of the check is that the shipped permission list is EXACTLY the set
the manifest justifies, one comment per permission — no permission arrives by
accident (a transitive library manifest merge is the usual way one does), and
none survives the feature that motivated it (RECORD_AUDIO did not).

Claims verified on the built APK:
  - the APK's permission set is EXACTLY the set the manifest declares: the
    default-SMS-handler role, POST_NOTIFICATIONS, the SMS/MMS runtime
    permissions, READ_CONTACTS, and RECEIVE_BOOT_COMPLETED — nothing more
  - the application is not backup-enabled (allowBackup=false), backing the
    local-first posture (nothing silently pushed to cloud backup)

Exits 0 on success, 1 on any failure.
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug",
                   "app-debug.apk")

# The exact permission set the manifest declares (app/src/main/AndroidManifest.xml).
# The APK must declare precisely these and nothing more.
EXPECTED_PERMISSIONS = {
    "android.permission.role.SMS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_SMS",
    "android.permission.RECEIVE_MMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
    "android.permission.WRITE_SMS",
    # Contact-name resolution: ContactsContract.PhoneLookup turns a number into
    # the name the operator saved for it (contacts/ContactNameResolver.kt).
    # READ only — a contacts WRITE permission is asserted absent below, because
    # naming a sender never requires modifying the contacts database.
    "android.permission.READ_CONTACTS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    # Family theme-sync: signature-level IPC with XX-Launcher. Not a dangerous
    # permission and not network — required to receive the launcher's
    # THEME_CHANGED broadcast (sendBroadcast with this permission name).
    "com.piercingxx.xxlauncher.permission.THEME_SYNC",
    # XX-Dialer Business-tier export: signature IPC, not network. Lets TxxT
    # honour the same 09:00–19:00 window the dialer uses for Business contacts.
    "com.piercingxx.xxdialer.permission.TIER_SYNC",
    # NOTE: no RECORD_AUDIO. WS13 declared the mic for the compose bar's
    # dictation button; the compose-bar rework deleted that button — its only
    # entry point — so the permission was removed with it. The APK must not
    # ask for a mic it cannot use.
}

# Permissions the app must never acquire: it reads contacts to name a sender
# and has no business editing the operator's address book.
FORBIDDEN_PERMISSIONS = {
    "android.permission.WRITE_CONTACTS",
    "android.permission.GET_ACCOUNTS",
}


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def parse_sdk_dir(props_path):
    """Return the sdk.dir value from local.properties, or None if absent."""
    with open(props_path, "r", encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m = re.match(r"sdk\.dir=(.+)$", line)
            if m:
                return m.group(1).strip()
    return None


def run(cmd, env=None):
    """Run a command, returning (exit_code, stdout)."""
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True,
                          check=False, env=env)
    return proc.returncode, proc.stdout


def java_home():
    """Return JAVA_HOME from the wired toolchain (sibling of the SDK)."""
    props_path = os.path.join(ROOT, "local.properties")
    sdk_dir = parse_sdk_dir(props_path)
    if sdk_dir is None:
        return None
    toolchain = os.path.dirname(sdk_dir)
    jdk = os.path.join(toolchain, "jdk17")
    if os.path.exists(os.path.join(jdk, "bin", "java")):
        return jdk
    return None


def main():
    # --- resolve the wired toolchain's JDK for the build ---
    jdk = java_home()
    check(jdk is not None, "wired toolchain provides a JDK (JAVA_HOME)")
    env = dict(os.environ)
    env["JAVA_HOME"] = jdk

    # --- run the build (the wired toolchain) ---
    rc, _ = run(["./gradlew", "assembleDebug", "--console=plain"], env=env)
    check(rc == 0, "./gradlew assembleDebug exits 0")

    # --- the APK must exist ---
    check(os.path.exists(APK), "app-debug.apk exists at the expected path")

    # --- locate aapt2 in the wired SDK's build-tools ---
    props_path = os.path.join(ROOT, "local.properties")
    sdk_dir = parse_sdk_dir(props_path)
    check(sdk_dir is not None, "local.properties sets sdk.dir")
    aapt2 = os.path.join(sdk_dir, "build-tools", "34.0.0", "aapt2")
    check(os.path.exists(aapt2), f"aapt2 exists at {aapt2}")

    # --- dump the APK's actual permission list (the README's command) ---
    rc, perms_out = run([aapt2, "dump", "permissions", APK])
    check(rc == 0, "aapt2 dump permissions exits 0")

    declared = set(re.findall(r"uses-permission: name='([^']+)'", perms_out))

    # --- the contacts read must not have grown into a write ---
    forbidden = FORBIDDEN_PERMISSIONS & declared
    check(not forbidden,
          "APK declares no contacts write / account permission "
          f"(found {sorted(forbidden)})")

    # --- the permission set must be exactly the privacy-preserving set ---
    # AGP auto-generates one synthetic signature-level permission for
    # non-exported receivers (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION). It is
    # app-scoped and grants nothing to other apps, so it is not a privacy leak.
    # Strip it, then the declared set must equal the manifest's set exactly.
    synthetic = {"com.piercingxx.txxt.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    check(declared - synthetic == EXPECTED_PERMISSIONS,
          "APK permission set is exactly the declared, justified set "
          f"(got {sorted(declared)})")

    # --- local-first: backups are disabled on the built artifact ---
    # aapt2 dump xmltree renders the merged manifest's application element,
    # where allowBackup=false appears as an explicit attribute.
    rc, xmltree = run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml",
                       APK])
    check(rc == 0, "aapt2 dump xmltree AndroidManifest.xml exits 0")
    check(":allowBackup(0x01010280)=false" in xmltree,
          "built manifest sets allowBackup=false (local-first, no cloud backup)")


if __name__ == "__main__":
    main()