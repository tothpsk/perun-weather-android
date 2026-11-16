#!/usr/bin/env python3
"""Builds the MeteoAlarm warning area outlines used by the app.

Input:  geocodes.json of the "meteoalarm" Python package (GeoJSON, one feature per EMMA_ID area).
Output: one file per country, <output>/<CC>.json, of the form
        {"SK706": [[lon, lat, lon, lat, ...], ...], ...}
        Every list is one closed ring. All rings of an area are kept in one flat list; a point is
        inside the area when it lies inside an odd number of its rings (this also handles holes).

Usage:  python3 tools/meteoalarm_areas.py /path/to/geocodes.json
        python3 tools/meteoalarm_areas.py /path/to/geocodes.json --tolerance 0.001 --output app/src/main/assets/meteoalarm

Only the Python standard library is needed.
"""
import argparse
import json
import os
import sys


def rings_of(geometry):
    kind = geometry.get("type")
    if kind == "Polygon":
        return list(geometry["coordinates"])
    if kind == "MultiPolygon":
        return [ring for polygon in geometry["coordinates"] for ring in polygon]
    return []


def distance(p, a, b):
    """Distance of the point p from the segment a-b, in degrees."""
    ax, ay, bx, by, px, py = a[0], a[1], b[0], b[1], p[0], p[1]
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    if length == 0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    return ((px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2) ** 0.5


def simplify(points, tolerance):
    """Douglas-Peucker without recursion; the first and the last point are always kept."""
    if len(points) < 3:
        return list(points)
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        best, index = 0.0, -1
        for i in range(first + 1, last):
            d = distance(points[i], points[first], points[last])
            if d > best:
                best, index = d, i
        if index >= 0 and best > tolerance:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [p for p, k in zip(points, keep) if k]


def closed(ring):
    ring = [(float(p[0]), float(p[1])) for p in ring]
    if ring and ring[0] != ring[-1]:
        ring.append(ring[0])
    return ring


def flat(ring, digits):
    out = []
    for x, y in ring:
        out.append(round(x, digits))
        out.append(round(y, digits))
    return out


def main():
    parser = argparse.ArgumentParser(description="Build the MeteoAlarm area outlines for the app.")
    parser.add_argument("geocodes", help="path to geocodes.json")
    parser.add_argument("--output", default=os.path.join("app", "src", "main", "assets", "meteoalarm"))
    parser.add_argument("--tolerance", type=float, default=0.001, help="simplification tolerance in degrees (0.001 is about 100 m)")
    parser.add_argument("--digits", type=int, default=4, help="decimal places of the coordinates (4 is about 11 m)")
    args = parser.parse_args()

    with open(args.geocodes, encoding="utf-8") as source:
        features = json.load(source)["features"]

    countries = {}
    dropped = kept_whole = skipped = 0
    for feature in features:
        properties = feature.get("properties") or {}
        code = str(properties.get("code") or "").strip()
        country = str(properties.get("country") or code[:2]).strip().upper()
        if properties.get("type") != "EMMA_ID" or not code or len(country) != 2 or not feature.get("geometry"):
            skipped += 1
            continue
        source_rings = [closed(r) for r in rings_of(feature["geometry"])]
        source_rings = [r for r in source_rings if len(r) >= 4]
        rings = []
        for ring in source_rings:
            simple = simplify(ring, args.tolerance)
            if len(simple) >= 4:
                rings.append(flat(simple, args.digits))
            else:
                dropped += 1
        if not rings and source_rings:
            # A very small area would vanish: keep its largest ring as it is.
            rings.append(flat(max(source_rings, key=len), args.digits))
            kept_whole += 1
        if not rings:
            skipped += 1
            continue
        countries.setdefault(country, {}).setdefault(code, []).extend(rings)

    os.makedirs(args.output, exist_ok=True)
    for name in os.listdir(args.output):
        if len(name) == 7 and name.endswith(".json"):
            os.remove(os.path.join(args.output, name))
    total_bytes = total_points = total_areas = 0
    for country in sorted(countries):
        areas = countries[country]
        text = json.dumps({code: areas[code] for code in sorted(areas)}, separators=(",", ":"))
        with open(os.path.join(args.output, country + ".json"), "w", encoding="utf-8", newline="\n") as target:
            target.write(text)
        points = sum(len(ring) // 2 for rings in areas.values() for ring in rings)
        total_bytes += len(text)
        total_points += points
        total_areas += len(areas)
        print(f"{country}: {len(areas)} areas, {points} points, {len(text)} bytes")
    print(f"Total: {len(countries)} countries, {total_areas} areas, {total_points} points, {total_bytes} bytes")
    print(f"Rings dropped as too small: {dropped}; areas kept unsimplified: {kept_whole}; features skipped: {skipped}")
    print(f"Written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
