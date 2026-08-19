#!/usr/bin/env python3
"""Verify the T2 local toolchain is wired correctly.

Checks, per the WS1 contract:
  - local.properties exists and carries an uncommented sdk.dir line
  - the SDK directory that sdk.dir points at actually exists on this box

The SDK directory is read from local.properties (the measured sibling-repo
convention), not hardcoded, so the check is portable across users and
environments. Exits 0 on success, 1 on any failure.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


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


def main():
    # --- local.properties exists and carries an uncommented sdk.dir line ---
    props_path = os.path.join(ROOT, "local.properties")
    check(os.path.exists(props_path),
          "local.properties exists")

    sdk_dir = parse_sdk_dir(props_path)
    check(sdk_dir is not None,
          "local.properties sets sdk.dir to the measured sibling convention")
    check(not sdk_dir.startswith("#"),
          "sdk.dir is not commented out")

    # --- the SDK directory the line points at actually exists on this box ---
    check(os.path.isdir(sdk_dir),
          f"SDK directory exists at {sdk_dir}")

    print("T2 toolchain verification passed.")


if __name__ == "__main__":
    main()