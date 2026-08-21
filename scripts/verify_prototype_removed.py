#!/usr/bin/env python3
"""Verify the T3 prototype feasibility test has been deleted.

Checks, per the TxxT-ws7a corrective plan:
  - app/src/test/kotlin/com/piercingxx/txxt/service/PrototypeFeasibilityTest.kt
    does NOT exist (the failing, non-deliverable prototype test)
  - no source file still references the prototype test class name

Exits 0 on success, 1 on any failure.
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROTOTYPE = os.path.join(
    ROOT,
    "app",
    "src",
    "test",
    "kotlin",
    "com",
    "piercingxx",
    "txxt",
    "service",
    "PrototypeFeasibilityTest.kt",
)


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    check(not os.path.exists(PROTOTYPE),
          "PrototypeFeasibilityTest.kt has been deleted")

    # Ensure nothing still references the deleted prototype class.
    src_root = os.path.join(ROOT, "app", "src")
    for dirpath, _dirnames, filenames in os.walk(src_root):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            check("PrototypeFeasibilityTest" not in content,
                  f"{os.path.relpath(path, ROOT)} does not reference the prototype test")

    print("T3 prototype-removal verification passed.")


if __name__ == "__main__":
    main()