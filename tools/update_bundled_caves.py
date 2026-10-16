"""
Refreshes app/src/main/assets/caves_hu.json, the cave catalogue shipped inside the APK.

Run before cutting a release:

    python tools/update_bundled_caves.py

Same Overpass query and the same border clipping as the app's "Fetch cave entrances… →
All of Hungary" (OverpassClient.fetchHungaryBbox + HungaryBoundary), so a fresh install
starts with exactly what a manual fetch would give. The output keeps Overpass's element
shape ({"elements": [{"id", "lat", "lon", "tags"}]}) so the app parses it with the same code.
"""
import json
import pathlib
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ASSETS / "caves_hu.json"

# Must match HungaryBoundary.SOUTH/WEST/NORTH/EAST.
SOUTH, WEST, NORTH, EAST = 45.70, 16.05, 48.62, 22.95

QUERY = f"""[out:json][timeout:60];
(
  node["natural"="cave_entrance"]({SOUTH},{WEST},{NORTH},{EAST});
  node["natural"="sinkhole"]({SOUTH},{WEST},{NORTH},{EAST});
);
out body;"""


def load_ring():
    ring = []
    for line in (ASSETS / "hungary_border.csv").read_text().splitlines():
        parts = line.split(",")
        if len(parts) == 2:
            ring.append((float(parts[0]), float(parts[1])))
    return ring


def inside(ring, lat, lon):
    """Even-odd ray cast, identical to HungaryBoundary.crossingNumber."""
    if lat < SOUTH or lat > NORTH or lon < WEST or lon > EAST:
        return False
    result = False
    j = len(ring) - 1
    for i in range(len(ring)):
        lat_i, lon_i = ring[i]
        lat_j, lon_j = ring[j]
        if (lat_i > lat) != (lat_j > lat):
            slope = (lon_j - lon_i) / (lat_j - lat_i)
            if lon < lon_i + slope * (lat - lat_i):
                result = not result
        j = i
    return result


def main():
    request = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=QUERY.encode(),
        headers={
            "Content-Type": "text/plain; charset=utf-8",
            "User-Agent": "lakatosmap-build/0.1 (bundled cave catalogue)",
        },
    )
    print("Querying Overpass...")
    with urllib.request.urlopen(request, timeout=120) as response:
        elements = json.load(response)["elements"]

    ring = load_ring()
    kept = [
        {
            "id": e["id"],
            "lat": round(e["lat"], 7),
            "lon": round(e["lon"], 7),
            "tags": e.get("tags", {}),
        }
        for e in elements
        if "lat" in e and inside(ring, e["lat"], e["lon"])
    ]
    kept.sort(key=lambda e: e["id"])

    OUT.write_text(
        json.dumps(
            {"generatedAtMs": int(time.time() * 1000), "elements": kept},
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    print(f"{len(kept)} of {len(elements)} entrances inside Hungary -> {OUT.relative_to(ROOT)} "
          f"({OUT.stat().st_size // 1024} kB)")


if __name__ == "__main__":
    main()
