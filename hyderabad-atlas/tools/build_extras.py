#!/usr/bin/env python3
"""Second data pass: adds real road geometry, ORR exits, the Musi river, MMTS
rail, Telugu names and landmark photos to web/data/atlas.json.

Run after build_data.py:
    python3 tools/build_data.py && python3 tools/build_extras.py

Highways are routed along the OSM major-road network (motorway/trunk/primary)
between hand-picked waypoints, preferring roads that carry the highway's ref
(e.g. NH44), so each corridor follows the real road instead of a straight line.
"""
import glob
import gzip
import heapq
import json
import math
import os
import re
import sys

from shapely.geometry import LineString, MultiLineString, Point, shape
from shapely.ops import linemerge

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
WEB = os.path.join(HERE, "..", "web")
ATLAS = os.path.join(WEB, "data", "atlas.json")
KX = math.cos(math.radians(17.4))


def load(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def km(a, b):
    """distance in km between (lng, lat) pairs"""
    return math.hypot((a[0] - b[0]) * 111.32 * KX, (a[1] - b[1]) * 110.57)


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def rnd(c):
    return [round(c[0], 5), round(c[1], 5)]


atlas = load(ATLAS)
warnings = []
loc_by_name = {l["name"]: l for l in atlas["localities"]}
domain = shape(atlas["domain"])

# ------------------------------------------------------------------ road graph
ways = {}
for path in sorted(glob.glob(os.path.join(RAW, "roads_*.json*"))):
    for e in load(path)["elements"]:
        if e["type"] == "way" and e.get("geometry"):
            ways[e["id"]] = e
print(f"{len(ways)} major road ways")

CLASS_COST = {"motorway": 0.8, "trunk": 0.85, "primary": 1.0, "motorway_link": 1.3, "trunk_link": 1.3}
nodes = {}       # key -> (lng, lat)
adj = {}         # key -> list of (key, length_km, way_id)


def key(p):
    return (round(p["lon"], 6), round(p["lat"], 6))


for wid, w in ways.items():
    pts = [key(p) for p in w["geometry"]]
    for a, b in zip(pts, pts[1:]):
        if a == b:
            continue
        nodes[a] = a
        nodes[b] = b
        d = km(a, b)
        adj.setdefault(a, []).append((b, d, wid))
        adj.setdefault(b, []).append((a, d, wid))
node_list = list(nodes)
print(f"graph: {len(node_list)} nodes")


def way_matches(w, prefer):
    if not prefer:
        return False
    tags = w.get("tags", {})
    text = " ".join([tags.get("ref", ""), tags.get("name", ""), tags.get("name:en", "")])
    return any(re.search(p, text, re.I) for p in prefer)


def nearest_node(pt, prefer=None, max_km=3.0):
    best, bd = None, max_km
    for n in node_list:
        d = km(n, pt)
        if d < bd:
            if prefer:
                # only snap to nodes on preferred roads
                if not any(way_matches(ways[wid], prefer) for _, _, wid in adj[n]):
                    continue
            bd, best = d, n
    return best


def route(a, b, prefer):
    """Dijkstra between two graph nodes; preferred roads are much cheaper."""
    dist = {a: 0.0}
    prev = {}
    pq = [(0.0, a)]
    while pq:
        d, u = heapq.heappop(pq)
        if u == b:
            break
        if d > dist.get(u, 1e18):
            continue
        for v, length, wid in adj[u]:
            w = ways[wid]
            cost = length * CLASS_COST.get(w["tags"].get("highway"), 1.2)
            if prefer and way_matches(w, prefer):
                cost *= 0.6
            nd = d + cost
            if nd < dist.get(v, 1e18):
                dist[v] = nd
                prev[v] = u
                heapq.heappush(pq, (nd, v))
    if b not in prev and a != b:
        return None
    path = [b]
    while path[-1] != a:
        path.append(prev[path[-1]])
    return path[::-1]


def stop_point(s):
    if isinstance(s, tuple):
        name, lat, lng = s
        return name, (lng, lat), None
    l = loc_by_name.get(s)
    if not l:
        raise KeyError(f"unknown stop {s}")
    return s, (l["lng"], l["lat"]), l["id"]


# name, ref (for display), prefer patterns, description, waypoints
CORRIDORS = [
    ("NH44 · Nagpur Highway", "NH44", [r"\bNH ?44\b", r"\bAH ?43\b"], "North from Paradise through Bowenpally and Kompally to Medchal",
     [("Paradise", 17.4437, 78.4877), "Bowenpally", "Suchitra", "Kompally", "Medchal"]),
    ("NH44 · Bengaluru Highway", "NH44", [r"\bNH ?44\b", r"\bAH ?43\b"], "South from Aramghar past Shamshabad and the airport turn-off",
     [("Aramghar", 17.3297, 78.4362), "Katedan", "Shamshabad", ("ORR Exit 16", 17.25173, 78.37762)]),
    ("NH65 · Mumbai Highway", "NH65", [r"\bNH ?65\b", r"Mumbai", r"Bombay"], "The old Bombay highway from Ameerpet out to Patancheru",
     ["Ameerpet", "Erragadda", "Moosapet", "Kukatpally", "Miyapur", "Chandanagar", "Patancheru"]),
    ("NH65 · Vijayawada Highway", "NH65", [r"\bNH ?65\b", r"Vijayawada"], "East from Malakpet through Dilsukhnagar and LB Nagar to Hayathnagar",
     ["Malakpet", "Dilsukh Nagar", "LB Nagar", "Vanasthalipuram", "Hayathnagar", "Abdullapurmet"]),
    ("NH163 · Warangal Highway", "NH163", [r"\bNH ?163\b", r"Warangal"], "East from Tarnaka through Uppal to Ghatkesar",
     ["Tarnaka", "Habsiguda", "Uppal", "Peerzadiguda", "Narapally", "Ghatkesar"]),
    ("Rajiv Rahadari · Karimnagar Highway", "SH1", [r"\bSH ?1\b", r"Rajiv", r"Karimnagar"], "North-east from JBS past Trimulgherry and Hakimpet to Shamirpet",
     [("JBS", 17.4466, 78.4985), ("Trimulgherry", 17.4693, 78.5108), ("Hakimpet", 17.5311, 78.5244), "Thumkunta", ("Shamirpet", 17.5946, 78.5562)]),
    ("Srisailam Highway", "NH765", [r"\bNH ?765\b", r"Srisailam"], "South from Chandrayangutta past Pahadi Shareef to Tukkuguda",
     ["Chandrayangutta", "Balapur", "Pahadi Sharif", ("Tukkuguda", 17.2052, 78.4754)]),
    ("Nagarjuna Sagar Highway", "", [r"Sagar", r"\bSH ?19\b"], "South-east from the Sagar Ring Road past Turkayamjal",
     [("Sagar Ring Road", 17.3418, 78.5418), "Bairamalguda", "Turkayamjal", ("ORR Exit 12", 17.23532, 78.58439)]),
    ("PVNR Expressway", "", [r"Narasimha", r"PVNR", r"Expressway"], "India's longest flyover (11.6 km) from Mehdipatnam to Aramghar",
     ["Mehdipatnam", "Attapur", ("Aramghar", 17.3297, 78.4362)]),
    ("Old Mumbai Highway", "", [r"Old Mumbai", r"Old Bombay"], "The IT corridor road from Mehdipatnam through Tolichowki to Gachibowli",
     ["Mehdipatnam", "Tolichowki", "Shaikpet", ("Biodiversity Junction", 17.4220, 78.3800), "Gachibowli", "Lingampally"]),
]

corridors = []
for name, ref, prefer, desc, stops in CORRIDORS:
    pts = [stop_point(s) for s in stops]
    line_nodes = []
    ok = True
    for (n1, p1, _), (n2, p2, _) in zip(pts, pts[1:]):
        a = nearest_node(p1, prefer, 2.5) or nearest_node(p1, None, 3.5)
        b = nearest_node(p2, prefer, 2.5) or nearest_node(p2, None, 3.5)
        seg = route(a, b, prefer) if a and b else None
        if seg and sum(km(x, y) for x, y in zip(seg, seg[1:])) > km(p1, p2) * 1.7 + 1:
            # preferring the named road made a big detour: take the plain shortest road path
            a2, b2 = nearest_node(p1, None, 3.5), nearest_node(p2, None, 3.5)
            seg = route(a2, b2, None) or seg
        if not seg:
            warnings.append(f"{name}: no road path {n1} -> {n2}, using straight line")
            seg = [p1, p2]
            ok = False
        line_nodes += seg if not line_nodes else seg[1:]
    line = LineString(line_nodes).simplify(0.00012)
    length = sum(km(a, b) for a, b in zip(line.coords, list(line.coords)[1:]))
    straight = sum(km(p1, p2) for (_, p1, _), (_, p2, _) in zip(pts, pts[1:]))
    if length > straight * 1.6:
        warnings.append(f"{name}: route {length:.1f} km vs straight {straight:.1f} km (detour?)")
    corridors.append({
        "id": "r-" + slug(name), "name": name, "ref": ref, "desc": desc,
        "line": [rnd(c) for c in line.coords], "lengthKm": round(length, 1), "routed": ok,
        "stops": [{"name": n, "id": lid, "lng": round(p[0], 5), "lat": round(p[1], 5)} for n, p, lid in pts],
    })
    print(f"  {name}: {length:.1f} km ({'road' if ok else 'partly straight'})")

# ------------------------------------------------------------------ ORR + exits
junctions = load(os.path.join(RAW, "junctions.json"))["elements"]
orr_line = atlas["orr"]["coordinates"]
orr_ls = LineString(orr_line)
exits = {}
for j in junctions:
    ref = j["tags"].get("ref")
    if not ref or not re.match(r"^\d+A?$", ref):
        continue
    p = (j["lon"], j["lat"])
    if orr_ls.distance(Point(p)) > 0.02:   # only exits on the ORR
        continue
    exits.setdefault(ref, {"names": [], "pts": []})
    if j["tags"].get("name"):
        exits[ref]["names"].append(j["tags"]["name"])
    exits[ref]["pts"].append(p)

EXIT_FALLBACK = {"1A": "Kokapet"}
exit_items = []
for ref, e in exits.items():
    lng = sum(p[0] for p in e["pts"]) / len(e["pts"])
    lat = sum(p[1] for p in e["pts"]) / len(e["pts"])
    nm = max(set(e["names"]), key=e["names"].count) if e["names"] else EXIT_FALLBACK.get(ref, f"Exit {ref}")
    nm = re.sub(r"\s*\((North|South)\)", "", nm)
    num = float(ref.rstrip("A")) + (0.5 if ref.endswith("A") else 0)
    exit_items.append({"id": "x-" + ref.lower(), "ref": ref, "num": num, "name": f"Exit {ref} · {nm}", "short": nm,
                       "lng": round(lng, 5), "lat": round(lat, 5)})
exit_items.sort(key=lambda e: e["num"])
print(f"ORR exits: {', '.join(e['ref'] for e in exit_items)}")

# order the ORR ring so it starts at exit 1 and runs in exit order
start = min(range(len(orr_line)), key=lambda i: km(orr_line[i], (exit_items[0]["lng"], exit_items[0]["lat"])))
ring = orr_line[start:] + orr_line[1:start + 1]
probe = min(range(len(ring)), key=lambda i: km(ring[i], (exit_items[3]["lng"], exit_items[3]["lat"])))
if probe > len(ring) / 2:
    ring = ring[::-1]
orr_len = sum(km(a, b) for a, b in zip(ring, ring[1:]))
corridors.append({
    "id": "r-orr", "name": "Outer Ring Road (ORR)", "ref": "ORR", "loop": True,
    "desc": f"The {orr_len:.0f} km expressway ring around the city with {len(exit_items)} exits",
    "line": [rnd(c) for c in ring], "lengthKm": round(orr_len, 1), "routed": True,
    "stops": [{"name": e["short"], "id": e["id"], "lng": e["lng"], "lat": e["lat"]} for e in exit_items],
})

# ------------------------------------------------------------------ Musi river
musi = load(os.path.join(RAW, "musi.json"))["elements"]
lines = [LineString([(p["lon"], p["lat"]) for p in w["geometry"]]) for w in musi if w.get("geometry")]
merged = linemerge(MultiLineString(lines))
parts = list(merged.geoms) if merged.geom_type == "MultiLineString" else [merged]
parts = [p.simplify(0.00015) for p in parts if p.length > 0.01]
rivers = [{"id": "w-musi", "name": "Musi River", "kind": "river",
           "fact": "The river that splits old Hyderabad from the newer city; Charminar's side is the south bank.",
           "lines": [[rnd(c) for c in p.coords] for p in parts]}]
print(f"Musi: {len(parts)} parts")

# ------------------------------------------------------------------ MMTS rail
rail = []
mmts_path = os.path.join(RAW, "mmts.json")
if os.path.exists(mmts_path):
    rels = [r for r in load(mmts_path)["elements"] if r.get("members")]
    seen = set()
    for r in sorted(rels, key=lambda r: -len(r["members"])):
        nm = r["tags"].get("name", "").replace("Hyderabad MMTS: ", "")
        ends = tuple(sorted(x.strip() for x in nm.split("→")))
        if ends in seen or len(ends) != 2:
            continue
        seen.add(ends)
        segs = [LineString([(p["lon"], p["lat"]) for p in m["geometry"]]) for m in r["members"]
                if m["type"] == "way" and m.get("geometry") and m.get("role", "") == ""]
        if not segs:
            continue
        mg = linemerge(MultiLineString(segs))
        longest = max(mg.geoms, key=lambda g: g.length) if mg.geom_type == "MultiLineString" else mg
        rail.append({"id": "rail-" + slug(" ".join(ends)), "name": f"MMTS {ends[0]} – {ends[1]}", "kind": "rail",
                     "line": [rnd(c) for c in longest.simplify(0.00015).coords]})
    print(f"MMTS lines: {len(rail)}")

# ------------------------------------------------------------------ Telugu names
places = load(os.path.join(RAW, "places.json"))["elements"]
te_index = {}
for e in places:
    t = e["tags"]
    if t.get("name") and t.get("name:te"):
        te_index.setdefault(norm(t["name"]), []).append(((e["lon"], e["lat"]), t["name:te"]))


def te_for(name, pt, max_km=2.5):
    best, bd = None, max_km
    for p, te in te_index.get(norm(name), []):
        d = km(p, pt)
        if d < bd:
            bd, best = d, te
    return best


n_te = 0
for l in atlas["localities"]:
    te = te_for(l["name"], (l["lng"], l["lat"]))
    if te:
        l["te"] = te
        n_te += 1
loc_by_id = {l["id"]: l for l in atlas["localities"]}
for c in atlas["circles"]:
    lead = next((l for l in atlas["localities"] if l["circle"] == c["id"] and norm(l["name"]) == norm(c["name"])), None)
    if lead and lead.get("te"):
        c["te"] = lead["te"]
for z in atlas["zones"]:
    same = next((c for c in atlas["circles"] if norm(c["name"]) == norm(z["name"]) and c.get("te")), None)
    if same:
        z["te"] = same["te"]
CORP_TE = {"ghmc": "జీహెచ్ఎంసీ", "cyberabad": "సైబరాబాద్", "malkajgiri": "మల్కాజిగిరి"}
for c in atlas["corporations"]:
    c["te"] = CORP_TE[c["id"]]
print(f"Telugu names: {n_te} areas, {sum(1 for c in atlas['circles'] if c.get('te'))} circles, "
      f"{sum(1 for z in atlas['zones'] if z.get('te'))} zones")

# ------------------------------------------------------------------ landmark photos
# photo_meta.json: Commons file + remote URLs for every landmark (fetch_photos.py --meta)
# photos.json: the subset downloaded and bundled into the app, with credits
meta_path = os.path.join(RAW, "photo_meta.json")
photos_path = os.path.join(RAW, "photos.json")
meta = load(meta_path) if os.path.exists(meta_path) else {}
local = load(photos_path) if os.path.exists(photos_path) else {}
n_local = 0
for m in atlas["landmarks"]:
    mt = meta.get(m["id"])
    if not mt:
        continue
    ph = {"remote": mt["remote"], "remoteThumb": mt["remoteThumb"], "source": mt["source"], "file": mt["file"]}
    lc = local.get(m["id"])
    if lc and os.path.exists(os.path.join(WEB, lc["hero"])) and os.path.exists(os.path.join(WEB, lc["thumb"])):
        ph.update({"hero": lc["hero"], "thumb": lc["thumb"], "author": lc["author"], "license": lc["license"]})
        n_local += 1
    m["photo"] = ph
    if mt.get("desc"):
        m["wdDesc"] = mt["desc"]
    if mt.get("te"):
        m["te"] = mt["te"]
print(f"landmark photos: {sum(1 for m in atlas['landmarks'] if m.get('photo'))}/{len(atlas['landmarks'])} "
      f"({n_local} bundled, rest loaded on the phone)")

# ------------------------------------------------------------------ write
atlas["corridors"] = corridors
atlas["exits"] = exit_items
atlas["rivers"] = rivers
atlas["rail"] = rail
with open(ATLAS, "w", encoding="utf-8") as f:
    json.dump(atlas, f, separators=(",", ":"), ensure_ascii=False)
print(f"wrote {ATLAS} ({os.path.getsize(ATLAS) / 1024:.0f} KB)")
if warnings:
    print("\nWarnings:")
    for w in warnings:
        print(" ", w)
