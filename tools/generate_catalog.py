#!/usr/bin/env python3
"""Regenerates app/src/main/assets/catalog from the official Renfe static GTFS files.

Usage: python tools/generate_catalog.py <long_distance.zip> <commuter.zip>
Sources and download URLs are listed in docs/fuentes-de-datos.md.
"""
import csv
import io
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/catalog"


def rows(archive, name):
    text = archive.read(name).decode("utf-8-sig", "replace")
    for row in csv.DictReader(io.StringIO(text)):
        yield {(k or "").strip(): (v or "").strip() for k, v in row.items()}


def write(name, header, data):
    with open(OUT / name, "w", encoding="utf-8", newline="\n") as f:
        f.write(";".join(header) + "\n")
        for row in data:
            f.write(";".join(str(c).replace(";", ",") for c in row) + "\n")


def main(long_distance, commuter):
    OUT.mkdir(parents=True, exist_ok=True)
    ld = zipfile.ZipFile(long_distance)
    cer = zipfile.ZipFile(commuter)

    stations = {}
    for archive in (cer, ld):  # Long-distance names win: they are the fuller ones.
        for s in rows(archive, "stops.txt"):
            stations[s["stop_id"]] = (s["stop_name"], round(float(s["stop_lat"]), 6), round(float(s["stop_lon"]), 6))
    write("stations.csv", ["id", "name", "lat", "lon"], [(k, *v) for k, v in sorted(stations.items())])

    lines = sorted(
        (r["route_id"], r["route_short_name"], r["route_color"], " ".join(r["route_long_name"].split()))
        for r in rows(cer, "routes.txt")
    )
    write("lines.csv", ["routeId", "line", "color", "name"], lines)

    products = {r["route_id"]: r["route_short_name"] for r in rows(ld, "routes.txt")}
    trains = {}
    for t in rows(ld, "trips.txt"):
        route = t["route_id"]
        trains.setdefault(t["trip_short_name"], (products.get(route, ""), route[:5], route[5:10]))
    write("trains.csv", ["number", "product", "origin", "destination"], [(k, *v) for k, v in sorted(trains.items())])
    print(f"{len(stations)} stations, {len(lines)} lines, {len(trains)} trains")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
