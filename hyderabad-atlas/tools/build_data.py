#!/usr/bin/env python3
"""Build web/data/atlas.json from OSM extracts in tools/raw and hierarchy.py.

    pip install shapely numpy
    python3 tools/build_data.py

Region borders are approximate: each locality gets a Voronoi cell, clipped to
the city boundary (ORR plus a margin). Cells merge upward into circles, zones
and corporations, so the four levels always nest exactly.
"""
import json
import math
import os
import re
import sys

from shapely.geometry import LineString, MultiPoint, Point, Polygon, mapping, shape
from shapely.ops import linemerge, polylabel, unary_union, voronoi_diagram
from shapely import affinity

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from hierarchy import CORPORATIONS, CIRCLES, LANDMARKS, CORRIDORS  # noqa: E402

RAW = os.path.join(HERE, "raw")
OUT = os.path.join(HERE, "..", "web", "data", "atlas.json")
LAT0 = 17.4
KX = math.cos(math.radians(LAT0))  # squash longitude so Voronoi works in ~metres


def load(name):
    with open(os.path.join(RAW, name)) as f:
        return json.load(f)


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def km(a, b):
    """Approx distance in km between (lat,lng) pairs."""
    dy = (a[0] - b[0]) * 110.57
    dx = (a[1] - b[1]) * 111.32 * KX
    return math.hypot(dx, dy)


def to_xy(geom):
    return affinity.scale(geom, xfact=KX, yfact=1.0, origin=(0, 0))


def to_ll(geom):
    return affinity.scale(geom, xfact=1.0 / KX, yfact=1.0, origin=(0, 0))


def area_km2(geom):
    return to_xy(geom).area * 110.57 * 110.57


def rnd(coords):
    if isinstance(coords, (list, tuple)) and coords and isinstance(coords[0], (int, float)):
        return [round(coords[0], 5), round(coords[1], 5)]
    return [rnd(c) for c in coords]


def geo(geom, tol=0.0):
    if tol:
        geom = geom.simplify(tol, preserve_topology=True)
    m = mapping(geom)
    return {"type": m["type"], "coordinates": rnd(json.loads(json.dumps(m["coordinates"])))}


# ------------------------------------------------------------------ places
places = load("places.json")["elements"]
by_name = {}
for e in places:
    n = e["tags"].get("name")
    if not n:
        continue
    for key in {norm(n), norm(e["tags"].get("name:en", n))}:
        by_name.setdefault(key, []).append((e["lat"], e["lon"], e["tags"].get("place")))

ALIASES = {
    "rajendranagar": ["rajendranagar", "rajendranagar"],
    "karvan": ["karvan", "karwan", "karwansahu"],
    "jangamet": ["jangamet", "jangammet"],
    "dilsukhnagar": ["dilsukhnagar", "dilkhushnagar"],
    "saroornagar": ["saroornagar"],
}


def resolve(name, anchor, warn):
    keys = ALIASES.get(norm(name), [norm(name)])
    cands = []
    for k in keys:
        cands += by_name.get(k, [])
    if not cands:
        warn.append(f"no OSM node for {name}")
        return None
    best = min(cands, key=lambda c: km((c[0], c[1]), anchor))
    d = km((best[0], best[1]), anchor)
    unique = len({(round(c[0], 3), round(c[1], 3)) for c in cands}) == 1
    limit = 9 if unique else 5
    if d > limit:
        warn.append(f"{name}: nearest OSM node {d:.1f} km from anchor, ignored")
        return None
    return (best[0], best[1])


# ------------------------------------------------------------------ hierarchy
warnings = []
corps, zones, circles, localities = [], [], [], []
for corp in CORPORATIONS:
    corps.append({k: corp[k] for k in ("id", "name", "full", "num", "color", "fact")})
    for zname, zfact in corp["zones"]:
        zid = "z-" + slug(zname)
        zones.append({"id": zid, "name": zname, "corp": corp["id"], "fact": zfact})
        for cname, wards, anchor, cfact, locs in CIRCLES[zname]:
            cid = "c-" + slug(cname)
            circles.append({"id": cid, "name": cname, "zone": zid, "corp": corp["id"],
                            "wards": wards, "fact": cfact})
            for item in locs:
                if isinstance(item, tuple):
                    lname, lat, lng = item
                    pos = (lat, lng)
                else:
                    lname = item
                    pos = resolve(lname, anchor, warnings)
                    if pos is None:
                        warnings.append(f"  -> dropped {lname} ({cname})")
                        continue
                display = re.sub(r"\s*\(.*\)$", "", lname)
                localities.append({"id": "l-" + slug(lname), "name": display,
                                   "circle": cid, "zone": zid, "corp": corp["id"],
                                   "lat": round(pos[0], 5), "lng": round(pos[1], 5)})

ids = [l["id"] for l in localities]
dups = {i for i in ids if ids.count(i) > 1}
assert not dups, dups

# ------------------------------------------------------------------ ORR
orr_rel = load("orr.json")["elements"][0]
orr_lines = [LineString([(p["lon"], p["lat"]) for p in m["geometry"]])
             for m in orr_rel["members"] if m["type"] == "way" and m.get("geometry")]
orr_band = unary_union([to_xy(l).buffer(0.0018) for l in orr_lines])
if orr_band.geom_type == "MultiPolygon":
    orr_band = max(orr_band.geoms, key=lambda g: g.area)
inner = max(orr_band.interiors, key=lambda r: Polygon(r).area)
orr_inner_xy = Polygon(inner)
orr_line = LineString(to_ll(orr_inner_xy.buffer(0.0018).exterior).coords)  # road centre-ish ring
orr_line = orr_line.simplify(0.0004)
print(f"ORR enclosed area ~{area_km2(to_ll(orr_inner_xy)):.0f} km2")

# ------------------------------------------------------------------ domain
pts_xy = [Point(l["lng"] * KX, l["lat"]) for l in localities]
domain_xy = unary_union([orr_inner_xy.buffer(0.012)] + [p.buffer(0.016) for p in pts_xy])
domain_xy = domain_xy.buffer(0.02).buffer(-0.02)  # closing: smooth the outline
if domain_xy.geom_type == "MultiPolygon":
    domain_xy = max(domain_xy.geoms, key=lambda g: g.area)
domain_xy = Polygon(domain_xy.exterior)
print(f"City domain ~{area_km2(to_ll(domain_xy)):.0f} km2")

# ------------------------------------------------------------------ voronoi
env = domain_xy.buffer(0.1)
vor = voronoi_diagram(MultiPoint(pts_xy), envelope=env)
cells = {}
for cell in vor.geoms:
    for i, p in enumerate(pts_xy):
        if i not in cells and cell.contains(p):
            cells[i] = cell.intersection(domain_xy)
            break
assert len(cells) == len(localities), (len(cells), len(localities))

for i, l in enumerate(localities):
    g = cells[i]
    if g.geom_type != "Polygon":
        g = max(g.geoms, key=lambda x: x.area) if hasattr(g, "geoms") else g
    cells[i] = g


def finish(items, groups, level):
    """Attach geometry, label point and area to each item."""
    for it in items:
        g_xy = unary_union(groups[it["id"]]).buffer(0)
        main = g_xy
        if g_xy.geom_type == "MultiPolygon":
            parts = sorted(g_xy.geoms, key=lambda x: -x.area)
            lost = sum(p.area for p in parts[1:]) / g_xy.area
            if lost > 0.05:
                warnings.append(f"{level} {it['name']} split in {len(parts)} parts ({lost:.0%} detached)")
            main = parts[0]
            g_xy = unary_union([Polygon(p.exterior) for p in parts])
        else:
            g_xy = Polygon(g_xy.exterior)
        it["_xy"] = g_xy
        it["_main"] = main
        lab = to_ll(polylabel(Polygon(main.exterior), tolerance=0.0005))
        it["label"] = [round(lab.x, 5), round(lab.y, 5)]
        it["areaKm2"] = round(area_km2(to_ll(g_xy)), 1)


loc_groups = {l["id"]: [cells[i]] for i, l in enumerate(localities)}
finish(localities, loc_groups, "locality")
circ_groups = {c["id"]: [l["_xy"] for l in localities if l["circle"] == c["id"]] for c in circles}
finish(circles, circ_groups, "circle")
zone_groups = {z["id"]: [c["_xy"] for c in circles if c["zone"] == z["id"]] for z in zones}
finish(zones, zone_groups, "zone")
corp_groups = {c["id"]: [z["_xy"] for z in zones if z["corp"] == c["id"]] for c in corps}
finish(corps, corp_groups, "corporation")


def neighbours(items):
    for a in items:
        a["near"] = []
    for i, a in enumerate(items):
        ga = a["_xy"].buffer(0.0006)
        for b in items[i + 1:]:
            inter = ga.intersection(b["_xy"].buffer(0.0006))
            if not inter.is_empty and inter.area > 0.0006 * 0.0006 * 6:
                a["near"].append(b["id"])
                b["near"].append(a["id"])


for level in (localities, circles, zones, corps):
    neighbours(level)

# ------------------------------------------------------------------ landmarks
lm_out = []
for name, cat, lat, lng, fact in LANDMARKS:
    p = Point(lng * KX, lat)
    home = next((l for l in localities if l["_xy"].contains(p)), None)
    if home is None:
        home = min(localities, key=lambda l: km((l["lat"], l["lng"]), (lat, lng)))
        inside = False
    else:
        inside = True
    lm_out.append({"id": "m-" + slug(name), "name": name, "cat": cat, "lat": lat, "lng": lng,
                   "fact": fact, "locality": home["id"], "circle": home["circle"],
                   "zone": home["zone"], "corp": home["corp"], "inCity": inside})

# ------------------------------------------------------------------ metro
metro_raw = load("metro.json")["elements"]
stations = load("stations.json")["elements"]
metro_stations = [s for s in stations if s["tags"].get("station") in ("subway", "Metro")
                  or "metro" in s["tags"].get("network", "").lower()]


def clean_station(n):
    n = re.sub(r"\s*\((Red|Blue|Green) Line\)", "", n)
    n = n.replace("L. B. Nagar Metro Station", "LB Nagar").replace("S. R. Nagar", "SR Nagar")
    n = n.replace("Mahatma Gandhi Bus Station", "MG Bus Station")
    return n.strip()


def stitch(ways):
    """Join consecutive way geometries into one line, flipping as needed."""
    line = []
    for w in ways:
        pts = [(p["lon"], p["lat"]) for p in w]
        if not line:
            line = pts
            continue
        ends = [(km(line[-1][::-1], pts[0][::-1]), False, False),
                (km(line[-1][::-1], pts[-1][::-1]), True, False),
                (km(line[0][::-1], pts[0][::-1]), False, True),
                (km(line[0][::-1], pts[-1][::-1]), True, True)]
        d, flip_new, flip_old = min(ends)
        if len(line) == len(ways[0]) and flip_old:
            line = line[::-1]
        if flip_new:
            pts = pts[::-1]
        line += pts[1:] if d < 0.05 else pts
    return line


LINES = [("red", "Red Line", "#E5484D", "Miyapur"),
         ("blue", "Blue Line", "#3E8BFF", "Nagole"),
         ("green", "Green Line", "#30C46C", "JBS")]
metro_out = []
for lid, lname, color, start in LINES:
    rels = [r for r in metro_raw if r["tags"].get("name", "").startswith(lname)]
    rel = max(rels, key=lambda r: len(r["members"]))
    ways = [m["geometry"] for m in rel["members"] if m["type"] == "way" and m.get("geometry")
            and m.get("role", "") in ("", "route")]
    coords = stitch(ways)
    stops = []
    for m in rel["members"]:
        if m["type"] != "node" or not m.get("role", "").startswith("stop"):
            continue
        lat, lon = m["lat"], m["lon"]
        best = min(metro_stations, key=lambda s: km((s["lat"], s["lon"]), (lat, lon)))
        if km((best["lat"], best["lon"]), (lat, lon)) > 0.4:
            continue
        raw = best["tags"]["name"]
        if "u/c" in raw:
            continue
        nm = clean_station(raw)
        if stops and stops[-1]["name"] == nm:
            continue
        stops.append({"name": nm, "lat": round(best["lat"], 5), "lng": round(best["lon"], 5)})
    if stops and stops[0]["name"] != start and stops[-1]["name"] == start:
        stops.reverse()
    if coords and stops and km(coords[0][::-1], (stops[0]["lat"], stops[0]["lng"])) > \
            km(coords[-1][::-1], (stops[0]["lat"], stops[0]["lng"])):
        coords.reverse()
    line_geom = LineString(coords).simplify(0.00015)
    metro_out.append({"id": lid, "name": lname, "color": color,
                      "line": rnd([list(c) for c in line_geom.coords]), "stations": stops})
    print(f"{lname}: {len(stops)} stations, {stops[0]['name']} -> {stops[-1]['name']}")

# ------------------------------------------------------------------ corridors
loc_by_name = {l["name"]: l for l in localities}
corr_out = []
for name, desc, color, stops in CORRIDORS:
    pts = []
    for s in stops:
        l = loc_by_name.get(s)
        if not l:
            warnings.append(f"corridor {name}: unknown stop {s}")
            continue
        pts.append({"name": s, "id": l["id"], "lat": l["lat"], "lng": l["lng"]})
    corr_out.append({"id": "r-" + slug(name), "name": name, "desc": desc, "stops": pts})

# ------------------------------------------------------------------ output
def export(items, tol, inset=0.0):
    out = []
    for it in items:
        d = {k: v for k, v in it.items() if not k.startswith("_")}
        d["geom"] = geo(to_ll(it["_xy"]), tol)
        if inset:
            # shrunken copy drawn as a raised slab, leaving dark gaps between regions
            slab = it["_xy"].buffer(-inset, join_style=1)
            if not slab.is_empty:
                d["slab"] = geo(to_ll(slab), tol)
        out.append(d)
    return out


atlas = {
    "meta": {
        "title": "Hyderabad Atlas",
        "structure": "3 corporations, 12 zones, 60 circles (G.O. 55, 11 Feb 2026)",
        "sources": [
            "Circle lists: Siasat / Telangana Today, Feb 2026",
            "Places, metro, ORR: (c) OpenStreetMap contributors (ODbL)",
            "Borders approximate: generated from locality positions",
        ],
        "areaKm2": round(area_km2(to_ll(domain_xy))),
    },
    "orr": geo(orr_line, 0),
    "domain": geo(to_ll(domain_xy), 0.0003),
    "corporations": export(corps, 0.0004, inset=0.0035),
    "zones": export(zones, 0.0003, inset=0.0013),
    "circles": export(circles, 0.0002),
    "localities": export(localities, 0.0001),
    "landmarks": lm_out,
    "metro": metro_out,
    "corridors": corr_out,
}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    json.dump(atlas, f, separators=(",", ":"))

print(f"\n{len(corps)} corporations, {len(zones)} zones, {len(circles)} circles, "
      f"{len(localities)} localities, {len(lm_out)} landmarks")
for c in corps:
    print(f"  {c['name']}: {c['areaKm2']} km2")
print(f"wrote {OUT} ({os.path.getsize(OUT) / 1024:.0f} KB)")
if warnings:
    print("\nWarnings:")
    for w in warnings:
        print(" ", w)
