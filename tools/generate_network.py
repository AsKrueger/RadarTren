#!/usr/bin/env python3
"""Builds app/src/main/assets/catalog/network.geojson from OpenStreetMap rail lines.

Usage: python tools/generate_network.py <overpass.json>
The query that produces the input is in docs/fuentes-de-datos.md. Data (c) OpenStreetMap
contributors, ODbL.
"""
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/catalog/network.geojson"
TOLERANCE_DEGREES = 0.0015  # About 150 m, finer than anything visible at network zoom levels.
DECIMALS = 4


def simplify(points):
    """Iterative Douglas-Peucker."""
    keep = {0, len(points) - 1}
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        ax, ay = points[first]
        bx, by = points[last]
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy) or 1e-12
        worst, index = 0.0, None
        for i in range(first + 1, last):
            px, py = points[i]
            distance = abs(dx * (ay - py) - dy * (ax - px)) / length
            if distance > worst:
                worst, index = distance, i
        if index is not None and worst > TOLERANCE_DEGREES:
            keep.add(index)
            stack += [(first, index), (index, last)]
    return [points[i] for i in sorted(keep)]


def merge(ways):
    """Joins ways that share an end, so simplification sees long lines rather than short pieces."""
    by_end = defaultdict(list)
    for index, way in enumerate(ways):
        by_end[way[0]].append(index)
        by_end[way[-1]].append(index)
    used = [False] * len(ways)

    def extend(line):
        while True:
            options = [i for i in by_end[line[-1]] if not used[i]]
            if not options:
                return line
            used[options[0]] = True
            way = ways[options[0]]
            line = line + (way[1:] if way[0] == line[-1] else way[::-1][1:])

    lines = []
    for index, way in enumerate(ways):
        if used[index]:
            continue
        used[index] = True
        lines.append(extend(extend(way)[::-1]))
    return lines


def main(path):
    elements = json.load(open(path, encoding="utf-8"))["elements"]
    groups = {0: [], 1: []}
    for way in elements:
        points = [(n["lon"], n["lat"]) for n in way.get("geometry", [])]
        if len(points) >= 2:
            groups[1 if way["tags"].get("highspeed") == "yes" else 0].append(points)

    features = []
    for high_speed, ways in groups.items():
        for line in merge(ways):
            coordinates = [[round(x, DECIMALS), round(y, DECIMALS)] for x, y in simplify(line)]
            features.append({
                "type": "Feature",
                "properties": {"hs": high_speed},
                "geometry": {"type": "LineString", "coordinates": coordinates},
            })

    OUT.write_text(json.dumps({"type": "FeatureCollection", "features": features}, separators=(",", ":")), "utf-8")
    print(f"{len(features)} lines, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main(sys.argv[1])
