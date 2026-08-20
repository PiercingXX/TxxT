#!/usr/bin/env python3
"""Verify the pure-JVM core/ module contains no android.* imports.

Per the WS2 contract, core/ must stay a pure-JVM module (kotlin.jvm, no
Android SDK dependency). Any `import android.` or `import androidx.` in a
Kotlin source file under core/ breaks that guarantee, so this gate scans
every .kt file in core/src and fails if any android-family import appears.

Exits 0 on success, 1 on any failure.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# An import of an Android framework or AndroidX class.
ANDROID_IMPORT = re.compile(r"^\s*import\s+(?:android|androidx)\.")

CORE_SRC = os.path.join(ROOT, "core", "src")


def iter_kotlin_files():
    for dirpath, _dirnames, filenames in os.walk(CORE_SRC):
        for name in filenames:
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def main():
    if not os.path.isdir(CORE_SRC):
        print("FAIL: core/src does not exist")
        sys.exit(1)

    offenders = []
    count = 0
    for path in iter_kotlin_files():
        count += 1
        with open(path, "r", encoding="utf-8") as f:
            for lineno, line in enumerate(f, start=1):
                if ANDROID_IMPORT.match(line):
                    rel = os.path.relpath(path, ROOT)
                    offenders.append(f"{rel}:{lineno}: {line.strip()}")

    if offenders:
        print("FAIL: android.* imports found in the pure-JVM core/ module:")
        for entry in offenders:
            print(f"  {entry}")
        sys.exit(1)

    print(f"ok: no android.* imports in {count} Kotlin source files under core/")
    print("T8 no-android-imports gate passed.")


if __name__ == "__main__":
    main()