#!/usr/bin/env python3
"""Verify the T2 local toolchain is wired correctly.

Checks, per the WS1 contract:
  - local.properties exists and carries the measured sibling-repo convention
    sdk.dir=/home/piercingxx/.local/android-toolchain/sdk
  - the SDK directory that sdk.dir points at actually exists on this box

Exits 0 on success, 1 on any failure.
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

EXPECTED_SDK_DIR = "/home/piercingxx/.local/android-toolchain/sdk"


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    # --- local.properties exists and carries the sdk.dir line ---
    props_path = os.path.join(ROOT, "local.properties")
    check(os.path.exists(props_path),
          "local.properties exists")

    with open(props_path, "r", encoding="utf-8") as f:
        props = f.read()

    check(f"sdk.dir={EXPECTED_SDK_DIR}" in props,
          "local.properties sets sdk.dir to the measured sibling convention")
    check("sdk.dir" in props and not props.strip().startswith("#"),
          "sdk.dir is not commented out")

    # --- the SDK directory the line points at actually exists ---
    check(os.path.isdir(EXPECTED_SDK_DIR),
          f"SDK directory exists at {EXPECTED_SDK_DIR}")
    check(os.path.isdir(os.path.join(EXPECTED_SDK_DIR, "platforms")),
          "SDK directory contains platforms/")
    check(os.path.isdir(os.path.join(EXPECTED_SDK_DIR, "build-tools")),
          "SDK directory contains build-tools/")

    print("T2 toolchain verification passed.")


if __name__ == "__main__":
    main()