#!/usr/bin/env python3
"""Visual regression for the app as it actually renders on a device.

Captures a named screen on a connected device and compares it with a stored baseline, reporting
the share of pixels that changed. Baseline PNGs live beside the script's `--dir` (default:
`artifacts/ux-audit/baselines`), which is untracked — the screenshots are local evidence, not
something to publish.

Deliberately NOT a pixel-exact comparison: the dashboard draws a live FPS chart, the status bar
carries a clock, and antialiasing moves with the GPU. A tolerance (default 2.5% changed pixels)
reports a layout change while ignoring a moving number. That is a weaker guarantee than a Compose
screenshot test and an honest one: it catches "the bar disappeared" and "the cards moved", not
"the padding changed by 1 dp".

Usage:
    python tools/ux_audit/screenshot_regression.py --serial 9f2fe49 --name gaming-tools
    python tools/ux_audit/screenshot_regression.py --serial 9f2fe49 --name gaming-tools --accept

Needs Pillow for the comparison; without it the script still captures (and says so).
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

DEFAULT_DIR = os.path.join("artifacts", "ux-audit", "baselines")


def capture(serial: str, path: str) -> None:
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(
            subprocess.run(
                ["adb", "-s", serial, "exec-out", "screencap", "-p"], capture_output=True
            ).stdout
        )


def compare(baseline: str, current: str) -> float | None:
    try:
        from PIL import Image, ImageChops
    except ImportError:
        return None
    a = Image.open(baseline).convert("RGB")
    b = Image.open(current).convert("RGB")
    if a.size != b.size:
        return 100.0
    diff = ImageChops.difference(a, b)
    # Count pixels whose channels moved by more than a hair, so 8-bit noise does not register.
    mask = diff.convert("L").point(lambda v: 255 if v > 12 else 0)
    # The mask holds only 0 and 255, so the histogram is the count without iterating pixels.
    changed = mask.histogram()[255]
    return 100.0 * changed / (a.size[0] * a.size[1])


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    ap.add_argument("--name", required=True, help="screen name, e.g. gaming-tools")
    ap.add_argument("--dir", default=DEFAULT_DIR)
    ap.add_argument("--tolerance", type=float, default=2.5, help="percent of pixels allowed to move")
    ap.add_argument("--accept", action="store_true", help="store this capture as the new baseline")
    args = ap.parse_args()

    baseline = os.path.join(args.dir, f"{args.name}.png")
    current = os.path.join(args.dir, f"{args.name}.current.png")
    capture(args.serial, current)

    if args.accept or not os.path.exists(baseline):
        os.replace(current, baseline)
        reason = "accepted" if args.accept else "no baseline existed"
        print(f"{args.name}: baseline written ({reason})")
        return 0

    changed = compare(baseline, current)
    if changed is None:
        print(f"{args.name}: captured, but Pillow is not installed so no comparison was made")
        return 0

    verdict = "PASS" if changed <= args.tolerance else "CHANGED"
    print(f"{args.name}: {changed:.2f}% pixels changed (tolerance {args.tolerance}%) — {verdict}")
    if verdict == "PASS":
        os.remove(current)
        return 0
    print(f"  baseline {baseline}")
    print(f"  current  {current}")
    print("  rerun with --accept if the change is intended")
    return 1


if __name__ == "__main__":
    sys.exit(main())
