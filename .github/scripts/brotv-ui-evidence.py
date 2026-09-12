#!/usr/bin/env python3
"""Validate fresh app-owned UI evidence before QA acts on it."""
import argparse
import re
import xml.etree.ElementTree as ET

PACKAGE = "com.brotv.iptv"


def bounds(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if match is None:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1, y1, x2, y2) if x2 > x1 and y2 > y1 else None


def actionable(node):
    return (node.get("package") == PACKAGE and node.get("clickable") == "true"
            and node.get("enabled") == "true" and bounds(node) is not None)


def ready(root):
    # An activity/window or a uniformly coloured splash is not usable content.
    return any(actionable(node) and any(
        child.get("package") == PACKAGE and
        (child.get("text", "").strip() or child.get("content-desc", "").strip())
        for child in node.iter()) for node in root.iter("node"))


def locate(root, target, prefix=False):
    def walk(node, ancestors):
        if node.get("package") == PACKAGE:
            labels = (node.get("text", ""), node.get("content-desc", ""))
            if any(label.startswith(target) if prefix else label == target for label in labels):
                for candidate in reversed(ancestors + [node]):
                    if actionable(candidate):
                        x1, y1, x2, y2 = bounds(candidate)
                        return (x1 + x2) // 2, (y1 + y2) // 2
        for child in node:
            found = walk(child, ancestors + [node])
            if found is not None:
                return found
        return None
    return walk(root, [])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xml")
    parser.add_argument("target", nargs="?")
    parser.add_argument("mode", nargs="?", choices=("exact", "prefix"), default="exact")
    args = parser.parse_args()
    try:
        root = ET.parse(args.xml).getroot()
    except (OSError, ET.ParseError):
        return 2
    if not ready(root):
        return 3
    if args.target is not None:
        point = locate(root, args.target, args.mode == "prefix")
        if point is None:
            return 4
        print(*point)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
