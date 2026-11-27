#!/usr/bin/env python3
"""Accessibility checks that need no human with TalkBack at hand.

Two questions can be answered from a `uiautomator dump`, and both are the ones a redesign breaks
first:

  * **Tap targets** — every clickable smaller than 48x48 dp. Material components
    (Button, IconButton, CheckBox, Switch, RadioButton) expand their touch target through
    `minimumInteractiveComponentSize` while drawing a smaller shape, and the dump reports the
    shape, so those are listed separately as informational. A custom clickable container has
    nothing to expand it: that is a finding.
  * **Clipped text** — a label cut off by the window edge, whose rendered bounds fall outside the
    screen. (An ellipsized label is NOT reported: it draws inside its bounds, so this catches a
    layout overflow, not a `maxLines` choice — see the vision check in screenshot review for that.)

Everything else about accessibility — reading order, contrast, whether TalkBack says something
useful — needs a person and is not claimed by this tool.

Usage:
    python tools/ux_audit/a11y_audit.py --serial 9f2fe49
    python tools/ux_audit/a11y_audit.py --serial 9f2fe49 --min-dp 48

Exit code is 1 when custom clickables fall under the floor, 0 otherwise, 2 if the tree is unreadable.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def sh(serial: str, *args: str) -> str:
    return (
        subprocess.run(
            ["adb", "-s", serial, *args], capture_output=True, check=False
        ).stdout.decode("utf-8", "replace").strip()
    )


def prop(serial: str, name: str) -> str:
    return sh(serial, "shell", "getprop", name)


def dump(serial: str, settle: float = 0.0) -> str:
    # A dump taken during a transition reports half-laid-out nodes: a 56 dp tab shows up as its
    # 32 dp clickable child and the audit cries wolf. Settle first, and re-read when a finding is
    # about to be reported, because a false alarm costs more than a second.
    if settle:
        time.sleep(settle)
    # A failed dump leaves the previous file behind and would silently audit the previous screen.
    sh(serial, "shell", "rm", "-f", "/sdcard/ui.xml")
    sh(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return subprocess.run(
        ["adb", "-s", serial, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True
    ).stdout.decode("utf-8", "replace")


def box(node) -> tuple[int, int, int, int] | None:
    raw = node.get("bounds") or ""
    try:
        left, rest = raw.split("][")
        return (
            int(left.strip("[").split(",")[0]),
            int(left.strip("[").split(",")[1]),
            int(rest.split(",")[0]),
            int(rest.split(",")[1].strip("]")),
        )
    except (ValueError, IndexError):
        return None


def walk(el):
    for child in el:
        yield child
        yield from walk(child)


def clipped_by_parent(root) -> set[int]:
    """Ids of nodes whose box is cut by an ancestor — a chip half off the viewport edge is not a
    18 dp target, it is an unmeasured one, and reporting it as a finding wastes a fix."""
    out: set[int] = set()
    stack = [(root, None)]
    while stack:
        node, parent_box = stack.pop()
        b = box(node)
        if b is not None and parent_box is not None:
            # A node flush against a scroll parent's edge is reported at its visible height, not
            # its real one, so it cannot be measured either way.
            if (b[1] <= parent_box[1] or b[3] >= parent_box[3]
                    or b[0] <= parent_box[0] or b[2] >= parent_box[2]):
                out.add(id(node))
        for child in node:
            stack.append((child, b if b is not None else parent_box))
    return out


def collect(root, px_per_dp: float, min_dp: float, screen) -> tuple[int, list, list, list]:
    small: list[tuple[str, float, float, str]] = []
    informational: list[tuple[str, float, float, str]] = []
    clipped: list[tuple[str, str]] = []
    clickable = 0
    clipped_nodes = clipped_by_parent(root)
    for node in walk(root):
        b = box(node)
        if b is None:
            continue
        w_dp = (b[2] - b[0]) / px_per_dp
        h_dp = (b[3] - b[1]) / px_per_dp
        label = (node.get("text") or node.get("content-desc") or "").strip()[:40]
        role = node.get("class", "").split(".")[-1]

        # A container clickable is usually a card: its own size is what the user hits.
        if id(node) in clipped_nodes and node.get("clickable") == "true":
            continue
        if node.get("clickable") == "true" or role in ("Button", "ImageButton", "CheckBox", "Switch"):
            clickable += 1
            if w_dp + 0.5 < min_dp or h_dp + 0.5 < min_dp:
                material = role in ("Button", "ImageButton", "CheckBox", "Switch", "RadioButton")
                (informational if material else small).append(
                    (label or role, w_dp, h_dp, node.get("bounds", ""))
                )

        # Text drawn past the window edge is a layout overflow, not a styling choice.
        if label and (b[0] < screen[0] - 1 or b[2] > screen[2] + 1):
            clipped.append((label, node.get("bounds", "")))

    return clickable, small, informational, clipped


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    ap.add_argument("--min-dp", type=float, default=48.0)
    args = ap.parse_args()

    density = int(prop(args.serial, "ro.sf.lcd_density") or 420)
    scale = sh(args.serial, "shell", "settings", "get", "system", "font_scale") or "?"
    px_per_dp = density / 160.0

    try:
        root = ET.fromstring(dump(args.serial, settle=0.4))
    except ET.ParseError:
        print("could not read the UI hierarchy (is a dialog covering the app?)")
        return 2

    # The hierarchy root carries no bounds; the first descendant that has them is the window.
    screen = next((box(n) for n in walk(root) if box(n)), (0, 0, 0, 0))
    print(
        f"screen      {screen[2] - screen[0]}x{screen[3] - screen[1]} px "
        f"= {(screen[2] - screen[0]) / px_per_dp:.0f}x{(screen[3] - screen[1]) / px_per_dp:.0f} dp"
    )
    print(f"density     {density} dpi   font_scale {scale}")

    clickable, small, informational, clipped = collect(root, px_per_dp, args.min_dp, screen)
    if small or clipped:
        try:
            settled = ET.fromstring(dump(args.serial, settle=1.2))
            clickable, small, informational, clipped = collect(
                settled, px_per_dp, args.min_dp, screen
            )
        except ET.ParseError:
            pass

    print(f"clickable   {clickable} nodes checked against {args.min_dp:.0f} dp")
    if small:
        print(f"\nSMALL TAP TARGETS ({len(small)})")
        for label, w_dp, h_dp, bounds in small:
            print(f"  {w_dp:5.1f} x {h_dp:5.1f} dp  {bounds:<24} {label}")
    else:
        print("small tap targets: none")

    if informational:
        print(f"\n  expanded by the component ({len(informational)}, informational)")
        for label, w_dp, h_dp, bounds in informational:
            print(f"  {w_dp:5.1f} x {h_dp:5.1f} dp  {bounds:<24} {label}")

    if clipped:
        print(f"\nCLIPPED BY THE WINDOW ({len(clipped)})")
        for label, bounds in clipped:
            print(f"  {bounds:<24} {label}")

    return 0 if not small else 1


if __name__ == "__main__":
    sys.exit(main())
