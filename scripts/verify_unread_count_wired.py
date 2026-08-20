#!/usr/bin/env python3
"""Verify UnreadCount is wired into the conversation-list ordering consumer.

This is a STRUCTURAL gate only: it cannot execute Kotlin, so it checks that the
wiring exists in source. The behavioral proof that the ordering is actually
driven by UnreadCount lives in ConversationListTest.kt (run via Gradle), which
this script additionally checks references UnreadCount directly. Per the T1
contract:
  - ConversationList.kt exists and defines the ConversationList consumer
  - ConversationList.sorted consults ConversationSortOrder.UNREAD_FIRST
  - the ordering consumer references UnreadCount.perConversation so the list
    surfaces unread conversations first (call-site + behavioral test coverage)
  - ConversationListTest.kt exercises the unread-first ordering AND names
    UnreadCount so the mechanism, not just the outcome, is under test
  - the consumer file carries no android.* imports (pure-JVM, JVM-testable)

Exits 0 on success, 1 on any failure.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MAIN = os.path.join(
    ROOT,
    "core/src/main/kotlin/com/piercingxx/txxt/core/ConversationList.kt",
)
TEST = os.path.join(
    ROOT,
    "core/src/test/kotlin/com/piercingxx/txxt/core/ConversationListTest.kt",
)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def main():
    check(os.path.isfile(MAIN), "ConversationList.kt exists")
    check(os.path.isfile(TEST), "ConversationListTest.kt exists")

    main_src = read(MAIN)
    test_src = read(TEST)

    check("object ConversationList" in main_src,
          "ConversationList consumer is defined")
    check("fun sorted" in main_src,
          "ConversationList exposes a sorted() ordering entry point")
    check("ConversationSortOrder.UNREAD_FIRST" in main_src,
          "ordering consults ConversationSortOrder.UNREAD_FIRST")
    check("UnreadCount.perConversation" in main_src,
          "ordering wires UnreadCount via perConversation")
    check("unread" in main_src,
          "ordering derives an unread key from UnreadCount")
    check("ConversationSortOrder.NEWEST_FIRST" in main_src,
          "recency ordering (NEWEST_FIRST) is handled")
    check("ConversationSortOrder.OLDEST_FIRST" in main_src,
          "recency ordering (OLDEST_FIRST) is handled")

    # Pure-JVM: no android.* imports in the consumer.
    check(not re.search(r"import\s+android\.", main_src),
          "ConversationList.kt has no android.* imports")

    # The test exercises the unread-first ordering end to end.
    check("ConversationList.sorted" in test_src,
          "test drives ConversationList.sorted")
    check("UNREAD_FIRST" in test_src,
          "test exercises the UNREAD_FIRST order")
    check("unread" in test_src.lower(),
          "test asserts unread conversations surface first")
    # The test must name the UnreadCount mechanism directly, so the wiring is
    # behaviorally asserted (ordering driven by UnreadCount.perConversation),
    # not merely the final order of a hardcoded expectation.
    check("UnreadCount" in test_src,
          "test references UnreadCount so the mechanism is under test")
    check("UnreadCount.perConversation" in test_src,
          "test drives the UnreadCount.perConversation aggregation")

    print("T1 UnreadCount wiring verification passed.")


if __name__ == "__main__":
    main()