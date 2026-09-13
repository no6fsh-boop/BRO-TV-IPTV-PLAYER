#!/usr/bin/env python3
"""Assert seeded history removal without changing other preference values."""
import sys
import xml.etree.ElementTree as ET


def values(root):
    if root.tag != "map":
        raise ValueError("Expected a SharedPreferences map")
    return {n.attrib["name"]: (n.tag, n.get("value"), (n.text or "").strip(),
            tuple(sorted((c.tag, (c.text or "").strip()) for c in n))) for n in root}


def belongs(key, kind):
    if kind == "clear-live":
        return key in {"live_last_category", "live_last_group", "live_cat_index", "live_channel_index"}
    if kind == "clear-movies":
        return key.startswith("position:movie:")
    if kind == "clear-series":
        return key.startswith(("position:episode:", "episode_series:", "last_episode:")) or key == "watched_episodes"
    raise ValueError("Unknown history kind")


def verify(before, after, kind):
    old, new = values(before), values(after)
    if not any(belongs(k, kind) for k in old):
        raise ValueError("No seeded history: an empty clear cannot prove deletion")
    if any(belongs(k, kind) for k in new):
        raise ValueError("History remains after clearing")
    if {k: v for k, v in old.items() if not belongs(k, kind)} != new:
        raise ValueError("Clearing changed unrelated preferences")


if __name__ == "__main__":
    try:
        verify(ET.parse(sys.argv[1]).getroot(), ET.parse(sys.argv[2]).getroot(), sys.argv[3])
    except (OSError, ET.ParseError, ValueError, KeyError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
