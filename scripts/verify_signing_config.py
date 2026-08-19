#!/usr/bin/env python3
"""Verify the orphaned signing-config reference is removed from app/build.gradle.

The WS1 scaffold originally declared a custom signingConfigs block that pointed
at a debug.keystore file that is not committed to the repo:

    signingConfigs {
        debug {
            storeFile file('debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }

That block referenced a keystore that does not exist in the tree, so the build
could not resolve it — an orphaned signing-config reference. The corrective
removes the block and relies on AGP's built-in debug signing config
(`signingConfig signingConfigs.debug`), which is always available and needs no
keystore file in the repo.

This regression verify pins that fix: it fails if any orphaned custom
signing-config block (one that references a keystore file) reappears, and
passes while the valid built-in debug signingConfig reference remains.

Exits 0 on success, 1 on any failure.
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    with open(os.path.join(ROOT, path), "r", encoding="utf-8") as f:
        return f.read()


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    app_build = read("app/build.gradle")

    # The orphaned reference: a custom signingConfigs block that points at a
    # keystore file. AGP's built-in debug signing config needs no storeFile, so
    # any storeFile reference is an orphaned custom keystore dependency.
    check("storeFile" not in app_build,
          "app/build.gradle has no storeFile reference to a keystore file")
    check("debug.keystore" not in app_build,
          "app/build.gradle has no debug.keystore reference")
    check("storePassword" not in app_build,
          "app/build.gradle has no hardcoded storePassword")
    check("keyAlias" not in app_build,
          "app/build.gradle has no hardcoded keyAlias")
    check("keyPassword" not in app_build,
          "app/build.gradle has no hardcoded keyPassword")

    # The custom signingConfigs block must not be declared at all.
    check("signingConfigs {" not in app_build,
          "app/build.gradle declares no custom signingConfigs block")

    # The valid built-in debug signing config reference may remain.
    check("signingConfig signingConfigs.debug" in app_build,
          "app/build.gradle still wires the built-in debug signing config")

    print("Signing-config verification passed.")


if __name__ == "__main__":
    main()