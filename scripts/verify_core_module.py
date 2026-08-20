#!/usr/bin/env python3
"""Verify the pure-JVM core/ module scaffold is wired correctly.

Checks, per the WS2 contract:
  - settings.gradle includes the ':core' module
  - top-level build.gradle declares org.jetbrains.kotlin.jvm (apply false)
  - core/build.gradle applies org.jetbrains.kotlin.jvm and declares JUnit
  - the core source directories exist (main + test Kotlin trees)

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
    # --- settings.gradle: core module included ---
    settings = read("settings.gradle")
    check("include ':core'" in settings,
          "settings.gradle includes the ':core' module")

    # --- top-level build.gradle: kotlin.jvm plugin declared ---
    root_build = read("build.gradle")
    check("org.jetbrains.kotlin.jvm" in root_build,
          "top-level build.gradle declares org.jetbrains.kotlin.jvm")
    check("apply false" in root_build,
          "plugins declared with apply false")

    # --- core/build.gradle: pure-JVM module ---
    core_build = read("core/build.gradle")
    check("org.jetbrains.kotlin.jvm" in core_build,
          "core module applies org.jetbrains.kotlin.jvm")
    check("junit:junit:4.13.2" in core_build,
          "core module declares JUnit 4.13.2")

    # --- core source directories exist ---
    check(os.path.isdir(os.path.join(ROOT, "core/src/main/kotlin")),
          "core/src/main/kotlin exists")
    check(os.path.isdir(os.path.join(ROOT, "core/src/test/kotlin")),
          "core/src/test/kotlin exists")

    print("T1 core scaffold verification passed.")


if __name__ == "__main__":
    main()