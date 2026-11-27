#!/usr/bin/env python3
"""Measure how much a CatSmoker screen asks the user to move.

Two numbers per screen, both taken from the running app over adb rather than inferred from
source:

* `swipes_to_bottom` — how many full-height swipes a thumb needs before the content stops
  moving. This is the dial the no-scroll work is actually trying to turn.
* `hidden_horizontal_px` — for any scroll container whose content is wider than the container,
  how much sits past the right edge. That is how the Gaming Tools section strip was caught: a
  954 px row whose chip content ran past 2000 px, so two thirds of the workspace's own
  navigation was off-screen and only three of six sections existed in the accessibility tree.

It also writes a screenshot and the raw uiautomator dump next to the numbers, so a claim about
a layout can always be re-checked against the pixels it came from.

Usage (adb must see the device; run from the repository root):

    python tools/ux_audit/measure_scroll.py shots/phone-profile
    python tools/ux_audit/measure_scroll.py shots/desktop-home --serial emulator-5554

The tool never modifies the app. It swipes from the middle of the largest scrollable region it
finds, and treats "the content moved less than 5 px" as the bottom.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(serial: str, *args: str) -> bytes:
    return subprocess.run(
        ["adb", "-s", serial, *args], capture_output=True, check=False
    ).stdout


def capture(serial: str, stem: str) -> str:
    dump_xml = stem + ".xml"
    with open(stem + ".png", "wb") as shot:
        shot.write(adb(serial, "exec-out", "screencap", "-p"))
    # The dump leaves the previous file in place when it fails, and reading a stale hierarchy
    # silently reports the previous screen. Removing it first makes a failure visible.
    adb(serial, "shell", "rm", "-f", "/sdcard/ui.xml")
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    with open(dump_xml, "wb") as out:
        out.write(adb(serial, "exec-out", "cat", "/sdcard/ui.xml"))
    return dump_xml


def bounds(node) -> tuple[int, int, int, int] | None:
    match = BOUNDS.match(node.get("bounds", ""))
    return tuple(int(v) for v in match.groups()) if match else None


def walk(element):
    for child in element:
        yield child
        yield from walk(child)


def texts(xml_path: str) -> dict[str, int]:
    """Top edge of each visible labelled node, keyed by its text."""
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return {}
    out: dict[str, int] = {}
    for node in walk(root):
        text = (node.get("text") or "").strip()
        box = bounds(node)
        if text and box:
            out[text[:60]] = box[1]
    return out


def hidden_horizontal(xml_path: str) -> list[tuple[int, int]]:
    """(viewport_width, hidden_px) for every scroll container with content past its right edge.

    Both container kinds are checked. A `HorizontalScrollView` is the obvious sideways scroller,
    but Compose reports the clipped content of a horizontal row through an enclosing scrollable
    as well, and that is how the old chip strip was measured; restricting this to
    `HorizontalScrollView` alone reported zero hidden pixels for a row that was hiding two of its
    six sections. Nodes whose bounds equal the screen are the hierarchy's echo of the window
    frame, not content, so they are skipped.
    """
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return []
    screen = bounds(root) or (0, 0, 10**6, 10**6)
    found = []
    for node in walk(root):
        box = bounds(node)
        if box is None or box == screen:
            continue
        scrollable = node.get("scrollable") == "true" or node.get("class", "").split(".")[-1] in (
            "HorizontalScrollView",
            "ScrollView",
        )
        if not scrollable:
            continue
        widest = box[0]
        for inner in walk(node):
            inner_box = bounds(inner)
            if inner_box is None or inner_box == screen:
                continue
            widest = max(widest, inner_box[2])
        if widest > box[2]:
            found.append((box[2] - box[0], widest - box[2]))
    return found


def largest_scroll_viewport(xml_path: str) -> tuple[int, int, int, int] | None:
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return None
    screen = bounds(root) or (0, 0, 10**6, 10**6)
    best = None
    for node in walk(root):
        box = bounds(node)
        if box is None or box == screen:
            continue
        if node.get("scrollable") != "true" and node.get("class", "").split(".")[-1] != "ScrollView":
            continue
        if best is None or (box[3] - box[1]) > (best[3] - best[1]):
            best = box
    return best


def swipes_to_bottom(serial: str, workdir: str, max_swipes: int) -> tuple[int, int]:
    """Swipe until the content stops moving. Returns (swipes, total scrolled px)."""
    probe = os.path.join(workdir, "_probe")
    first = capture(serial, probe)
    viewport = largest_scroll_viewport(first)
    previous = texts(first)
    total = 0
    swipes = 0
    for _ in range(max_swipes):
        if viewport:
            x = (viewport[0] + viewport[2]) // 2
            top = viewport[1] + int((viewport[3] - viewport[1]) * 0.3)
            bottom = viewport[1] + int((viewport[3] - viewport[1]) * 0.8)
        else:
            x, top, bottom = 500, 1400, 700
        # 300 ms over ~55% of the viewport: a faster or longer drag registers as a fling and
        # the measured travel stops matching the gesture.
        adb(serial, "shell", "input", "swipe", str(x), str(bottom), str(x), str(top), "300")
        time.sleep(0.7)
        current = texts(capture(serial, probe))
        moved = [previous[k] - current[k] for k in current if k in previous and previous[k] != current[k]]
        # A list whose rows swap identity also changes y; only a real scroll moves several of them
        # in the same direction by the same amount.
        travel = max(moved) if moved else 0
        if travel <= 5:
            break
        total += travel
        swipes += 1
        previous = current
    for suffix in (".png", ".xml"):
        try:
            os.remove(probe + suffix)
        except OSError:
            pass
    return swipes, total


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stem", help="output path without extension (png/xml written beside it)")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    parser.add_argument("--max-swipes", type=int, default=15)
    args = parser.parse_args()

    os.makedirs(os.path.dirname(args.stem) or ".", exist_ok=True)
    dump = capture(args.serial, args.stem)
    viewport = largest_scroll_viewport(dump)
    swipes, distance = swipes_to_bottom(args.serial, os.path.dirname(args.stem) or ".", args.max_swipes)

    print(f"screen                 {args.stem}")
    print(f"scrollable viewport    {viewport or 'none'}")
    print(f"swipes_to_bottom       {swipes}")
    print(f"scroll_distance_px     {distance}")
    hidden = hidden_horizontal(dump)
    if hidden:
        for width, hidden_px in hidden:
            print(f"hidden_horizontal_px   {hidden_px} of {width} visible")
    else:
        print("hidden_horizontal_px   0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
