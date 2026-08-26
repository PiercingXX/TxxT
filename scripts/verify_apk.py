#!/usr/bin/env python3
"""Verify the T5 built APK carries the WS1 permission posture.

Runs `./gradlew assembleDebug` (using the wired toolchain) and then inspects
the resulting APK with `aapt2 dump badging` to assert it declares the
default-SMS-handler role and POST_NOTIFICATIONS — proving the built artifact
carries the role the app needs to function as the SMS handler.

Exits 0 on success, 1 on any failure.
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug",
                   "app-debug.apk")


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

    # --- dump badging and assert the declared permission posture ---
    rc, badging = run([aapt2, "dump", "badging", APK])
    check(rc == 0, "aapt2 dump badging exits 0")

    check("uses-permission: name='android.permission.role.SMS'"
          in badging,
          "APK declares the default-SMS-handler role")
    check("uses-permission: name='android.permission.POST_NOTIFICATIONS'"
          in badging,
          "APK declares POST_NOTIFICATIONS")

    print("T5 APK verification passed.")


if __name__ == "__main__":
    main()