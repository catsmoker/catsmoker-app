#!/usr/bin/env python3
"""Which controls announce nothing useful to a screen reader.

TalkBack reads a clickable node, then merges the text of its descendants. So the interesting
question is not "does this node have a label" but "does anything inside this node have one": an
icon-only `IconButton` with `contentDescription = null` nested under a `clickable` row is silent,
while a chip's `Text` label is read even though the chip node itself carries no text.

Two findings, both from one `uiautomator dump`:

  * **unlabelled** — a clickable whose whole subtree has no text and no content description. A user
    on TalkBack hears "double tap to activate" and nothing else.
  * **ambiguous** — two or more distinct controls on the same screen with the identical label and
    no positional context ("Delete", "Delete" — which one?). A list of two is a warning, not a bug;
    the counts are printed so a real duplication stands out.

Deliberately NOT reported: decorative icons (correctly null), container clickables whose children
carry the words (that IS the label), and anything clipped by its parent (measured, elsewhere).

Usage:
    python tools/ux_audit/a11y_names.py --serial 9f2fe49
Exit code 1 when an unlabelled control exists, 0 otherwise, 2 if the tree cannot be read.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def sh(serial: str, *args: str) -> str:
    return subprocess.run(
        ["adb", "-s", serial, *args], capture_output=True, check=False
    ).stdout.decode("utf-8", "replace").strip()


def dump(serial: str, settle: float = 0.4) -> str:
    # A dump taken mid-transition reports half-built nodes, and a failed dump leaves the previous
    # file behind and would audit the previous screen.
    time.sleep(settle)
    sh(serial, "shell", "rm", "-f", "/sdcard/ui.xml")
    sh(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return subprocess.run(
        ["adb", "-s", serial, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True
    ).stdout.decode("utf-8", "replace")


def walk(el):
    for child in el:
        yield child
        yield from walk(child)


def box(node):
    raw = node.get("bounds") or ""
    try:
        left, rest = raw.split("][")
        return (
            int(left.strip("[").split(",")[0]), int(left.strip("[").split(",")[1]),
            int(rest.split(",")[0]), int(rest.split(",")[1].strip("]")),
        )
    except (ValueError, IndexError):
        return None


def clipped_ids(root) -> set[int]:
    """Nodes cut by their scroll parent report a sliver of themselves (a 4 px strip of a chip).
    Their label is not missing, it is off-screen — the same false alarm the tap-target audit had."""
    out: set[int] = set()
    stack = [(root, None)]
    while stack:
        node, parent = stack.pop()
        b = box(node)
        if b is not None and parent is not None:
            if (b[1] <= parent[1] or b[3] >= parent[3]
                    or b[0] <= parent[0] or b[2] >= parent[2]):
                out.add(id(node))
        for child in node:
            stack.append((child, b if b is not None else parent))
    return out


def label_of(node) -> str:
    """The words a screen reader would read for this node: its own, else its subtree's."""
    own = (node.get("text") or "").strip() or (node.get("content-desc") or "").strip()
    if own:
        return own
    parts = [
        (n.get("text") or "").strip() or (n.get("content-desc") or "").strip()
        for n in walk(node)
    ]
    return " ".join(p for p in parts if p).strip()


def is_control(node) -> bool:
    # Clickable is the honest test. A node that merely carries a Button/Switch *role* without being
    # clickable is a label or a container — Compose emits those, and counting them reports the
    # screen's own text as unlabelled controls.
    if node.get("clickable") == "true":
        return True
    return node.get("class", "").split(".")[-1] == "EditText"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    ap.add_argument("--label", default="", help="screen name to print with the findings")
    args = ap.parse_args()

    try:
        root = ET.fromstring(dump(args.serial))
    except ET.ParseError:
        print("could not read the UI hierarchy (is a dialog covering the app?)")
        return 2

    def collect(tree):
        found_controls = 0
        found_unlabelled = []
        found_labels = {}
        clipped = clipped_ids(tree)
        for node in walk(tree):
            if not is_control(node) or id(node) in clipped:
                continue
            found_controls += 1
            role = node.get("class", "").split(".")[-1]
            text = label_of(node)
            if not text:
                found_unlabelled.append((role, node.get("bounds", "")))
            else:
                found_labels[text] = found_labels.get(text, 0) + 1
        return found_controls, found_unlabelled, found_labels

    controls, unlabelled, labels = collect(root)
    if unlabelled:
        # Re-read once: the same false alarm the tap-target audit had, for the same reason.
        try:
            controls, unlabelled, labels = collect(ET.fromstring(dump(args.serial, settle=1.0)))
        except ET.ParseError:
            pass

    name = args.label or "screen"
    print(f"{name}: {controls} controls")
    if unlabelled:
        print(f"\nUNLABELLED ({len(unlabelled)})")
        for role, bounds in unlabelled:
            print(f"  {bounds:<24} {role}")
    else:
        print("unlabelled: none")

    repeated = {k: v for k, v in labels.items() if v > 1}
    if repeated:
        print(f"\nrepeated labels ({len(repeated)}; check they are distinguishable in context)")
        for text, count in sorted(repeated.items(), key=lambda kv: -kv[1])[:12]:
            print(f"  x{count}  {text[:60]}")

    return 0 if not unlabelled else 1


if __name__ == "__main__":
    sys.exit(main())
