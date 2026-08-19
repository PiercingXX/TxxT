#!/usr/bin/env python3
"""Verify the T4 underlined-XX adaptive icon is present and well-formed.

Checks, per the WS1 contract:
  - the four adaptive-icon files exist:
      res/mipmap-anydpi-v26/ic_launcher.xml
      res/mipmap-anydpi-v26/ic_launcher_round.xml
      res/drawable/ic_launcher_foreground.xml
      res/values/ic_launcher_colors.xml
  - the two adaptive-icon XMLs are proper <adaptive-icon> elements that
    reference @drawable/ic_launcher_foreground and a background color
  - the colors XML defines ic_launcher_background as brand Ink (#000000)
  - the foreground drawable is a valid 108x108 <vector> whose <path> geometry
    encodes the logomark's two-X-plus-underline stroke structure: two distinct
    diagonal crosses (each a pair of crossing strokes) plus one horizontal
    underline stroke, all stroked in white (#FFFFFF)

The geometry is checked structurally (parsing pathData commands), not by
substring matching, so a malformed or placeholder file cannot pass.

Exits 0 on success, 1 on any failure.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RES = os.path.join(ROOT, "app", "src", "main", "res")

IC_LAUNCHER = os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml")
IC_LAUNCHER_ROUND = os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml")
FOREGROUND = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
COLORS = os.path.join(RES, "values", "ic_launcher_colors.xml")


def check(cond, msg):
    if not cond:
        print(f"FAIL: {msg}")
        sys.exit(1)
    print(f"ok: {msg}")


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def path_commands(path_data):
    """Split a pathData string into a list of (command, [coords...]) tuples."""
    tokens = re.findall(r"[A-Za-z]|[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", path_data)
    commands = []
    i = 0
    while i < len(tokens):
        tok = tokens[i]
        if re.match(r"[A-Za-z]", tok):
            cmd = tok
            i += 1
            coords = []
            while i < len(tokens) and not re.match(r"[A-Za-z]", tokens[i]):
                coords.append(float(tokens[i]))
                i += 1
            commands.append((cmd, coords))
        else:
            i += 1
    return commands


def is_diagonal_cross(commands):
    """True if the path draws a single X: two diagonal strokes that cross.

    We require exactly two 'M' moveto anchors and two 'L' lines whose
    endpoints form two crossing diagonals (the four endpoints are distinct and
    the two segments share no endpoint but their bounding boxes overlap).
    """
    segs = []
    cur = None
    for cmd, coords in commands:
        if cmd == "M" and len(coords) >= 2:
            cur = (coords[0], coords[1])
        elif cmd == "L" and len(coords) >= 2 and cur is not None:
            segs.append((cur, (coords[0], coords[1])))
            cur = (coords[0], coords[1])
    if len(segs) != 2:
        return False
    (ax, ay), (bx, by) = segs[0]
    (cx, cy), (dx, dy) = segs[1]
    endpoints = {(ax, ay), (bx, by), (cx, cy), (dx, dy)}
    if len(endpoints) != 4:
        return False  # shares an endpoint -> not a clean cross
    # Both segments must be diagonal (non-axis-aligned).
    if ax == bx or ay == by or cx == dx or cy == dy:
        return False
    # The two diagonals must cross: bounding boxes overlap.
    seg1_x = sorted((ax, bx))
    seg1_y = sorted((ay, by))
    seg2_x = sorted((cx, dx))
    seg2_y = sorted((cy, dy))
    return (seg1_x[0] < seg2_x[1] and seg2_x[0] < seg1_x[1]
            and seg1_y[0] < seg2_y[1] and seg2_y[0] < seg1_y[1])


def is_underline(commands):
    """True if the path draws a single horizontal underline stroke."""
    segs = []
    cur = None
    for cmd, coords in commands:
        if cmd == "M" and len(coords) >= 2:
            cur = (coords[0], coords[1])
        elif cmd == "L" and len(coords) >= 2 and cur is not None:
            segs.append((cur, (coords[0], coords[1])))
            cur = (coords[0], coords[1])
    if len(segs) != 1:
        return False
    (ax, ay), (bx, by) = segs[0]
    return ay == by and ax != bx  # horizontal, non-degenerate


def main():
    # --- the four files exist ---
    for path, label in (
        (IC_LAUNCHER, "mipmap-anydpi-v26/ic_launcher.xml"),
        (IC_LAUNCHER_ROUND, "mipmap-anydpi-v26/ic_launcher_round.xml"),
        (FOREGROUND, "drawable/ic_launcher_foreground.xml"),
        (COLORS, "values/ic_launcher_colors.xml"),
    ):
        check(os.path.exists(path), f"{label} exists")

    # --- adaptive-icon XMLs reference the foreground drawable + a background ---
    for path, label in ((IC_LAUNCHER, "ic_launcher.xml"),
                        (IC_LAUNCHER_ROUND, "ic_launcher_round.xml")):
        content = read(path)
        check("<adaptive-icon" in content, f"{label} is an <adaptive-icon>")
        check("@drawable/ic_launcher_foreground" in content,
              f"{label} references the foreground drawable")
        check("<background" in content and "<foreground" in content,
              f"{label} declares both background and foreground")

    # --- colors XML defines the Ink background ---
    colors = read(COLORS)
    check("<color name=\"ic_launcher_background\">" in colors,
          "colors XML defines ic_launcher_background")
    check("#000000" in colors,
          "ic_launcher_background is brand Ink (#000000)")

    # --- foreground vector geometry: two X's + underline ---
    fg = read(FOREGROUND)
    check("<vector" in fg and "android:viewportWidth=\"108\"" in fg
          and "android:viewportHeight=\"108\"" in fg,
          "foreground is a 108x108 vector drawable")

    crosses = 0
    underlines = 0
    for m in re.finditer(r"android:pathData=\"([^\"]+)\"", fg):
        data = m.group(1)
        commands = path_commands(data)
        if is_diagonal_cross(commands):
            crosses += 1
        elif is_underline(commands):
            underlines += 1

    check(crosses == 2, f"foreground encodes two diagonal X crosses (found {crosses})")
    check(underlines == 1, f"foreground encodes one underline stroke (found {underlines})")

    check(fg.count("#FFFFFF") >= 3,
          "the logomark strokes are white (#FFFFFF)")

    print("T4 adaptive-icon verification passed.")


if __name__ == "__main__":
    main()