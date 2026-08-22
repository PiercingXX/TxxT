#!/usr/bin/env python3
"""Verify the T1 README states the WS16 privacy posture as fact.

Checks, per the WS16 contract (contracts/TxxT.md):
  - README.md exists
  - it states the no-INTERNET claim as fact
  - it states the no-analytics / no-crash-reporting claim as fact
  - it states the local-first posture as fact

Exits 0 on success, 1 on any failure.
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
README = os.path.join(ROOT, "README.md")


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    check(os.path.exists(README), "README.md exists")

    with open(README, "r", encoding="utf-8") as f:
        content = f.read()

    check("INTERNET" in content and "no `INTERNET` permission" in content,
          "README states the no-INTERNET permission claim")
    check("no analytics" in content and "no crash reporting" in content,
          "README states the no-analytics / no-crash-reporting claim")
    check("Local-first" in content,
          "README states the local-first posture")
    check("off by default" in content,
          "README states the privacy-leaks-are-opt-in default posture")

    print("T1 README privacy verification passed.")


if __name__ == "__main__":
    main()