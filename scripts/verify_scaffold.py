#!/usr/bin/env python3
"""Verify the T1 Gradle scaffold is wired correctly.

Checks, per the WS1 contract:
  - settings.gradle includes the :app module
  - top-level build.gradle declares AGP 8.5.0 and Kotlin 1.9.24 (apply false)
  - gradle.properties sets android.useAndroidX=true
  - app/build.gradle carries namespace com.piercingxx.txxt, compileSdk 34,
    minSdk 24, targetSdk 34, viewBinding true, Room 2.6.1 (+ kapt compiler,
     Room schema export), and the no-Gson backup-parser rule
  - the Gradle 8.7 wrapper files exist (gradlew, gradlew.bat,
    gradle/wrapper/gradle-wrapper.properties)

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
    # --- settings.gradle: app module included ---
    settings = read("settings.gradle")
    check("include ':app'" in settings,
          "settings.gradle includes the ':app' module")
    check("rootProject.name" in settings,
          "settings.gradle sets rootProject.name")

    # --- top-level build.gradle: plugins declared ---
    root_build = read("build.gradle")
    check("com.android.application" in root_build and "8.5.0" in root_build,
          "top-level build.gradle declares AGP 8.5.0")
    check("org.jetbrains.kotlin.android" in root_build and "1.9.24" in root_build,
          "top-level build.gradle declares Kotlin 1.9.24")
    check("apply false" in root_build,
          "plugins declared with apply false")

    # --- gradle.properties ---
    props = read("gradle.properties")
    check("android.useAndroidX=true" in props,
          "gradle.properties sets android.useAndroidX=true")

    # --- app/build.gradle: required config ---
    app_build = read("app/build.gradle")
    check("com.android.application" in app_build,
          "app module applies com.android.application")
    check("org.jetbrains.kotlin.android" in app_build,
          "app module applies org.jetbrains.kotlin.android")
    check("org.jetbrains.kotlin.kapt" in app_build,
          "app module applies the kapt plugin")
    check("namespace 'com.piercingxx.txxt'" in app_build,
          "app module namespace is com.piercingxx.txxt")
    check('applicationId "com.piercingxx.txxt"' in app_build,
          "app module applicationId is com.piercingxx.txxt")
    check("compileSdk 34" in app_build,
          "app module compileSdk is 34")
    check("minSdk 24" in app_build,
          "app module minSdk is 24")
    check("targetSdk 34" in app_build,
          "app module targetSdk is 34")
    check("viewBinding true" in app_build,
          "app module enables viewBinding")
    check("androidx.room:room-runtime:2.6.1" in app_build,
          "app module declares Room 2.6.1")
    check("androidx.room:room-compiler:2.6.1" in app_build,
          "app module declares the Room kapt compiler 2.6.1")
    check("room.schemaLocation" in app_build,
          "app module exports the Room schema via kapt")
    # Gson was removed (backup JSON is parsed exclusively by the validating
    # core BackupSerializer — no lenient second parser may come back).
    check("com.google.code.gson" not in app_build,
          "app module does not declare Gson (validating BackupSerializer only)")

    # --- Gradle 8.7 wrapper files exist ---
    check(os.path.exists(os.path.join(ROOT, "gradlew")),
          "gradlew exists")
    check(os.path.exists(os.path.join(ROOT, "gradlew.bat")),
          "gradlew.bat exists")
    wrapper_props = read("gradle/wrapper/gradle-wrapper.properties")
    check("gradle-8.7-bin.zip" in wrapper_props,
          "wrapper points at the Gradle 8.7 distribution")

    print("T1 scaffold verification passed.")


if __name__ == "__main__":
    main()