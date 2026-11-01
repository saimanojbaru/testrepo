#!/usr/bin/env python3
"""Third data pass: history (name meanings, name stories, Wikipedia links,
landmark years, timeline) and the Gen Z "vibes" profile of every circle.

Run after build_data.py and build_extras.py:
    python3 tools/build_v3.py
"""
import glob
import gzip
import json
import math
import os
import re
import sys
import urllib.parse

from shapely.geometry import Point, shape

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from history import SUFFIXES, NAME_STORIES, LANDMARK_YEARS, ERAS  # noqa: E402

RAW = os.path.join(HERE, "raw")
ATLAS = os.path.join(HERE, "..", "web", "data", "atlas.json")
KX = math.cos(math.radians(17.4))


def load(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def km(a, b):
    return math.hypot((a[0] - b[0]) * 111.32 * KX, (a[1] - b[1]) * 110.57)


atlas = load(ATLAS)
by_name = {m["name"]: m for m in atlas["landmarks"]}

# ------------------------------------------------------------------ name meanings
# endings that can also be a whole word ("Kishan Bagh", "Lakdi-ka-pul")
STANDALONE = {"nagar", "hills", "colony", "bagh", "bazaar", "bazar", "gunj", "pul", "jagir", "kalan",
              "sagar", "nagaram", "darwaza", "chowki", "mandi", "pahadi"}


def meanings(name):
    out = []
    for word in re.split(r"[\s-]+", name):
        w = word.lower()
        for suf, label, text in SUFFIXES:
            if w.endswith(suf) and (len(w) > len(suf) or suf in STANDALONE):
                out.append({"part": label, "meaning": text})
                break
    seen, uniq = set(), []
    for m in out:
        if m["part"] not in seen:
            seen.add(m["part"])
            uniq.append(m)
    return uniq


n_mean = n_story = 0
for kind in ("localities", "circles", "zones"):
    for it in atlas[kind]:
        mm = meanings(it["name"])
        if mm:
            it["nameParts"] = mm
            n_mean += 1
        st = NAME_STORIES.get(it["name"])
        if st:
            it["story"] = {"kind": st[0], "text": st[1]}
            n_story += 1
print(f"name meanings: {n_mean} places, name stories: {n_story}")

# ------------------------------------------------------------------ Wikipedia links
wd = load(os.path.join(RAW, "wikidata_articles.json"))["results"]["bindings"]
items = {}
for b in wd:
    c = b["coord"]["value"].replace("Point(", "").replace(")", "").split()
    art = urllib.parse.unquote(b["article"]["value"].split("/wiki/")[-1])
    items.setdefault(b["item"]["value"], {"label": b["itemLabel"]["value"], "article": art, "pt": (float(c[0]), float(c[1]))})
idx = {}
for it in items.values():
    for k in {norm(it["label"]), norm(it["article"].replace("_", " ").split(",")[0])}:
        idx.setdefault(k, []).append(it)
SKIP = re.compile(r"constituency|railway|metro|station|mandal|division|school|college|hospital|temple|masjid|church", re.I)
n_wiki = 0
for kind in ("localities", "circles"):
    for it in atlas[kind]:
        pt = (it["lng"], it["lat"]) if "lng" in it else tuple(it["label"])
        cands = [x for x in idx.get(norm(it["name"]), []) if km(x["pt"], pt) < 4 and not SKIP.search(x["article"])]
        if cands:
            best = min(cands, key=lambda x: km(x["pt"], pt))
            it["wiki"] = best["article"]
            n_wiki += 1
print(f"Wikipedia articles linked: {n_wiki}")

# ------------------------------------------------------------------ landmark years + timeline
for name, year in LANDMARK_YEARS.items():
    if name in by_name:
        by_name[name]["year"] = year
    else:
        print("  year for unknown landmark:", name)
eras = []
for start, end, title, sub, events in ERAS:
    evs = []
    for year, text, lm in events:
        e = {"year": year, "text": text}
        if lm:
            if lm not in by_name:
                print("  timeline landmark missing:", lm)
            else:
                e["id"] = by_name[lm]["id"]
        evs.append(e)
    eras.append({"start": start, "end": end, "title": title, "sub": sub, "events": evs})
atlas["eras"] = eras
print(f"timeline: {len(eras)} eras, {sum(len(e['events']) for e in eras)} events, "
      f"{sum(1 for m in atlas['landmarks'] if 'year' in m)} dated landmarks")

# ------------------------------------------------------------------ Gen Z vibes
CATS = {
    "cafe": ("Cafés & desserts", ("cafe", "ice_cream")),
    "food": ("Food spots", ("restaurant", "fast_food", "food_court")),
    "night": ("Night out", ("bar", "pub", "nightclub", "biergarten")),
    "fun": ("Malls & movies", ("mall", "cinema", "theatre", "arts_centre", "amusement_arcade", "bowling_alley", "escape_game", "water_park")),
    "fit": ("Gyms & sport", ("fitness_centre", "sports_centre", "stadium")),
    "campus": ("Campus", ("college", "university")),
    "green": ("Parks", ("park",)),
}
TYPE_CAT = {t: c for c, (_, types) in CATS.items() for t in types}
GENERIC = re.compile(r"^(ghmc|huda|municipal|colony|public|children'?s|community|new|ward)?\s*(park|playground|garden)s?$|playground|^park$", re.I)

polys = [(l["circle"], shape(l["geom"])) for l in atlas["localities"]]
circle_geom = {c["id"]: shape(c["geom"]) for c in atlas["circles"]}
pois = []
for path in sorted(glob.glob(os.path.join(RAW, "poi_*.json*"))):
    for e in load(path)["elements"]:
        t = e.get("tags", {})
        typ = t.get("amenity") or t.get("shop") or t.get("leisure")
        cat = TYPE_CAT.get(typ)
        if not cat:
            continue
        c = e.get("center") or {"lat": e.get("lat"), "lon": e.get("lon")}
        if c.get("lat") is None:
            continue
        pois.append((cat, t.get("name", ""), c["lon"], c["lat"]))
print(f"hangout places: {len(pois)}")

counts = {c["id"]: {k: 0 for k in CATS} for c in atlas["circles"]}
spots = {c["id"]: {k: [] for k in CATS} for c in atlas["circles"]}
for cat, name, lng, lat in pois:
    p = Point(lng, lat)
    cid = next((cid for cid, g in circle_geom.items() if g.contains(p)), None)
    if not cid:
        continue
    counts[cid][cat] += 1
    nm = name.strip()
    if nm and not GENERIC.search(nm) and len(nm) <= 40 and nm.lower() not in [s["name"].lower() for s in spots[cid][cat]]:
        spots[cid][cat].append({"name": nm, "lng": round(lng, 5), "lat": round(lat, 5)})


def pct_rank(values, v):
    return sum(1 for x in values if x <= v) / len(values)


WEIGHT = {"cafe": 2.0, "food": 1.0, "night": 1.5, "fun": 1.5, "fit": 0.8, "campus": 1.0, "green": 0.6}
TAG = {"cafe": "Café hopping", "food": "Foodie lanes", "night": "Night out", "fun": "Malls & movies",
       "fit": "Gym crowd", "campus": "Campus energy", "green": "Green & chill"}
scores = {cid: sum(WEIGHT[k] * n for k, n in cnt.items()) / max(1.0, circle_geom[cid].area * 110.57 * 111.32 * KX) ** 0.35
          for cid, cnt in counts.items()}
score_list = list(scores.values())
cat_lists = {k: [counts[cid][k] for cid in counts] for k in CATS}
for c in atlas["circles"]:
    cnt = counts[c["id"]]
    tags = []
    for k in sorted(CATS, key=lambda k: -WEIGHT[k] * cnt[k]):
        if cnt[k] >= 3 and pct_rank(cat_lists[k], cnt[k]) >= 0.75:
            tags.append(TAG[k])
    rank = {k: 1 + sum(1 for x in cat_lists[k] if x > cnt[k]) for k in CATS}
    c["vibe"] = {
        "counts": cnt,
        "score": round(100 * pct_rank(score_list, scores[c["id"]])),
        "tags": tags[:3] or (["Quiet & residential"] if sum(cnt.values()) < 6 else ["Mixed bag"]),
        "rank": rank,
        "spots": {k: v[:5] for k, v in spots[c["id"]].items() if v},
    }
atlas["vibeCats"] = {k: v[0] for k, v in CATS.items()}
top = sorted(atlas["circles"], key=lambda c: -c["vibe"]["score"])[:5]
print("top vibe circles:", [(c["name"], c["vibe"]["score"], c["vibe"]["tags"]) for c in top])

with open(ATLAS, "w", encoding="utf-8") as f:
    json.dump(atlas, f, separators=(",", ":"), ensure_ascii=False)
print(f"wrote {ATLAS} ({os.path.getsize(ATLAS) / 1024:.0f} KB)")
