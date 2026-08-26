#!/usr/bin/env python3
"""Verify the T3 manifest declares the WS1 privacy posture.

Checks, per the WS1 contract:
  - app/src/main/AndroidManifest.xml exists
  - it declares the default-SMS-handler role (android.permission.role.SMS)
  - it declares POST_NOTIFICATIONS
  - it declares READ_CONTACTS (contact-name resolution) and does NOT declare a
    contacts WRITE permission — the lookup is read-only

Exits 0 on success, 1 on any failure.
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    check(os.path.exists(MANIFEST),
          "app/src/main/AndroidManifest.xml exists")

    with open(MANIFEST, "r", encoding="utf-8") as f:
        content = f.read()

    check("android.permission.role.SMS" in content,
          "manifest declares the default-SMS-handler role")
    check("android.permission.POST_NOTIFICATIONS" in content,
          "manifest declares POST_NOTIFICATIONS")
    # Contact-name resolution (ContactsContract.PhoneLookup) needs the read
    # permission and nothing more: TxxT names a number from the operator's
    # saved contacts and never writes to the contacts database.
    check("android.permission.READ_CONTACTS" in content,
          "manifest declares READ_CONTACTS for contact-name resolution")
    check("android.permission.WRITE_CONTACTS" not in content,
          "manifest does not declare a contacts write permission (read-only)")

    print("T3 manifest verification passed.")


if __name__ == "__main__":
    main()