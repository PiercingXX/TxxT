#!/usr/bin/env python3
"""Verify the T1 README states the WS16 privacy posture as fact.

Checks, per the WS16 contract (contracts/TxxT.md):
  - README.md exists
  - it states the no-analytics / no-crash-reporting claim as fact
  - it states the local-first posture as fact
  - it states the privacy-leaks-are-opt-in default posture as fact

Deliberately NOT checked: any "no network / nothing leaves the device" claim.
The app handles SMS and MMS, which traverse the carrier network by definition,
and MMS in particular moves over carrier data through the system messaging
stack. A missing INTERNET permission means this process opens no sockets of its
own; it does not mean the operator's messages stay on the handset, and the
README must not imply otherwise. The claims below are the ones the product can
actually honour.
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

    check("no analytics" in content and "no crash reporting" in content,
          "README states the no-analytics / no-crash-reporting claim")
    check("Local-first" in content,
          "README states the local-first posture")
    check("off by default" in content,
          "README states the privacy-leaks-are-opt-in default posture")

    print("T1 README privacy verification passed.")


if __name__ == "__main__":
    main()