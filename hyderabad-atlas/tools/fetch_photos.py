#!/usr/bin/env python3
"""Download landmark photos from Wikimedia Commons with author + licence.

Inputs: tools/raw/wikidata_places.json (SPARQL result: places near Hyderabad
with an image and an English Wikipedia article) and WD_LABEL below.
Outputs: web/img/places/<id>.jpg (hero, 640px), <id>_t.jpg (96px pin) and
tools/raw/photos.json with credits. Polite: one request every few seconds,
retries on HTTP 429.

    python3 tools/fetch_photos.py
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from hierarchy import LANDMARKS  # noqa: E402

RAW = os.path.join(HERE, "raw")
OUT = os.path.join(HERE, "..", "web", "img", "places")
UA = "HyderabadAtlas/1.1 (https://github.com/saimanojbaru/testrepo; saimanojbaru@gmail.com)"

# landmark display name -> Wikidata English label (when they differ)
WD_LABEL = {
    "Qutb Shahi Tombs": "Qutb Shahi tombs", "Buddha Statue": "Buddha Statue of Hyderabad",
    "Dr. B.R. Ambedkar Statue": "Dr. B.R. Ambedkar statue", "Birla Mandir": "Birla Mandir, Hyderabad",
    "Public Gardens": "Public Gardens, Hyderabad", "KBR National Park": "Kasu Brahmananda Reddy National Park",
    "Cyber Towers": "HITEC City", "Moula Ali Dargah": "Moula-Ali",
    "Secunderabad Junction": "Secunderabad Junction railway station", "Nampally (Hyderabad Deccan)": "Hyderabad Deccan railway station",
    "Kacheguda Station": "Kacheguda railway station", "Charlapalli Terminal": "Charlapalli Railway Station",
    "MGBS Bus Station": "Mahatma Gandhi Bus Station", "JBS Bus Station": "Jubilee Bus Station",
    "Rajiv Gandhi Intl. Airport": "Rajiv Gandhi International Airport", "Fox Sagar": "Fox Sagar Lake",
    "Rajiv Gandhi Intl. Cricket Stadium": "Rajiv Gandhi International Cricket Stadium",
    "Mahavir Harina Vanasthali": "Mahavir Harina Vanasthali National Park", "Clock Tower Secunderabad": "Secunderabad Clock Tower",
    "Osman Sagar (Gandipet)": "Osman Sagar", "St. Joseph's Cathedral": "St Joseph's Cathedral",
    "St. George's Church": "St. George's Church, Hyderabad", "St. John's Church": "St. John's Church, Secunderabad",
    "St. Mary's Basilica": "Basilica of Our Lady of the Assumption, Secunderabad", "Parsi Fire Temple": "Parsi Fire Temple, Secunderabad",
    "Jagannath Temple": "Jagannath Temple, Hyderabad", "Peddamma Temple": "Peddamma temple",
    "British Residency": "British Residency, Hyderabad", "Purana Pul": "Purana pul",
    "Botanical Gardens": "Kotla Vijayabhaskara Reddy Botanical Gardens", "Lulu Mall": "Lulu International Shopping Mall, Hyderabad",
    "GMC Balayogi Stadium": "G. M. C. Balayogi Athletic Stadium", "IIIT Hyderabad": "International Institute of Information Technology, Hyderabad",
    "Telangana Secretariat": "Dr. B.R. Ambedkar Telangana State Secretariat", "Bella Vista (ASCI)": "Bella Vista",
    "Golconda Fort": "Golconda", "Durgam Cheruvu Cable Bridge": "Durgam Cheruvu Bridge", "Telangana State Archaeology Museum": "Telangana State Archaeology Museum",
}


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def get(url, binary=False, tries=6):
    for i in range(tries):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                data = r.read()
                return data if binary else data.decode("utf-8")
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = 20 * (i + 1)
                print(f"    429, waiting {wait}s")
                time.sleep(wait)
                continue
            print(f"    HTTP {e.code} for {url[:90]}")
            return None
        except Exception as e:  # network hiccup
            print(f"    error {e}")
            time.sleep(5)
    return None


def thumb_url(filename, width=500):
    fn = filename.replace(" ", "_")
    h = hashlib.md5(fn.encode()).hexdigest()
    q = urllib.parse.quote(fn)
    return f"https://upload.wikimedia.org/wikipedia/commons/thumb/{h[0]}/{h[:2]}/{q}/{width}px-{q}"


LICENSES = [
    (r"cc-by-sa-4\.0", "CC BY-SA 4.0"), (r"cc-by-sa-3\.0", "CC BY-SA 3.0"), (r"cc-by-sa-2\.5", "CC BY-SA 2.5"),
    (r"cc-by-sa-2\.0", "CC BY-SA 2.0"), (r"cc-by-4\.0", "CC BY 4.0"), (r"cc-by-3\.0", "CC BY 3.0"), (r"cc-by-2\.0", "CC BY 2.0"),
    (r"cc-zero|cc0", "CC0"), (r"pd-|public domain", "Public domain"), (r"gfdl", "GFDL"),
]


def credit_for(filename):
    txt = get("https://commons.wikimedia.org/w/index.php?title=" + urllib.parse.quote("File:" + filename) + "&action=raw") or ""
    lic = next((name for pat, name in LICENSES if re.search(pat, txt, re.I)), "see Wikimedia Commons")
    m = re.search(r"\|\s*[Aa]uthor\s*=\s*(.+)", txt)
    author = m.group(1).strip() if m else ""
    author = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]+)\]\]", r"\1", author)        # [[User:x|Name]] -> Name
    author = re.sub(r"\[https?://\S+\s+([^\]]+)\]", r"\1", author)            # [url Name] -> Name
    author = re.sub(r"\{\{[Cc]reator:([^}|]+)[^}]*\}\}", r"\1", author)
    author = re.sub(r"\{\{[^}]*\}\}", "", author)
    author = re.sub(r"<[^>]+>|'''?", "", author).strip(" .")
    if not author or len(author) > 60:
        author = "Wikimedia Commons contributor"
    return author, lic


def telugu_labels(qids):
    if not qids:
        return {}
    values = " ".join("wd:" + q for q in qids)
    q = f'SELECT ?item ?te WHERE {{ VALUES ?item {{ {values} }} ?item rdfs:label ?te FILTER(LANG(?te) = "te") }}'
    txt = get("https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(q))
    if not txt:
        return {}
    return {b["item"]["value"].split("/")[-1]: b["te"]["value"] for b in json.loads(txt)["results"]["bindings"]}


def write_meta():
    """Record the Commons file + remote thumbnail for every landmark (no image
    downloads). The app loads remote photos on the phone when no bundled copy
    exists, and fetches author/licence from the Commons API on demand."""
    wd = json.load(open(os.path.join(RAW, "wikidata_places.json")))["results"]["bindings"]
    by_label = {}
    for b in wd:
        by_label.setdefault(b["itemLabel"]["value"], b)
    meta = {}
    for name, cat, lat, lng, fact in LANDMARKS:
        b = by_label.get(WD_LABEL.get(name, name))
        if not b:
            print(f"no Wikidata match: {name}")
            continue
        filename = urllib.parse.unquote(b["img"]["value"].split("Special:FilePath/")[-1])
        meta["m-" + slug(name)] = {
            "file": filename, "remote": thumb_url(filename, 500), "remoteThumb": thumb_url(filename, 250),
            "source": "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(filename.replace(" ", "_")),
            "wd": b["item"]["value"].split("/")[-1], "desc": b.get("desc", {}).get("value", ""),
        }
    te = telugu_labels([m["wd"] for m in meta.values()])
    for m in meta.values():
        if m["wd"] in te:
            m["te"] = te[m["wd"]]
    json.dump(meta, open(os.path.join(RAW, "photo_meta.json"), "w"), indent=1, ensure_ascii=False)
    print(f"meta for {len(meta)} landmarks, {len(te)} Telugu labels")


def main():
    if "--meta" in sys.argv:
        write_meta()
        return
    os.makedirs(OUT, exist_ok=True)
    wd = json.load(open(os.path.join(RAW, "wikidata_places.json")))["results"]["bindings"]
    by_label = {}
    for b in wd:
        by_label.setdefault(b["itemLabel"]["value"], b)
    out_path = os.path.join(RAW, "photos.json")
    photos = json.load(open(out_path)) if os.path.exists(out_path) else {}

    todo = []
    for name, cat, lat, lng, fact in LANDMARKS:
        lid = "m-" + slug(name)
        b = by_label.get(WD_LABEL.get(name, name))
        if not b:
            print(f"no Wikidata match: {name}")
            continue
        todo.append((lid, name, b))

    for lid, name, b in todo:
        hero = f"img/places/{lid[2:]}.jpg"
        thumb = f"img/places/{lid[2:]}_t.jpg"
        have = photos.get(lid)
        if have and os.path.exists(os.path.join(OUT, os.path.basename(hero))):
            continue
        filename = urllib.parse.unquote(b["img"]["value"].split("Special:FilePath/")[-1])
        print(f"{name}: {filename}")
        data = get(thumb_url(filename), binary=True)
        if not data:
            continue
        src = os.path.join(OUT, "_src.jpg")
        open(src, "wb").write(data)
        # hero 640px wide, pin 96x96 centre crop, both small JPEGs
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", "scale='min(640,iw)':-2", "-q:v", "5",
                        os.path.join(OUT, os.path.basename(hero))], check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-vf", "scale=96:96:force_original_aspect_ratio=increase,crop=96:96",
                        "-q:v", "6", os.path.join(OUT, os.path.basename(thumb))], check=True)
        os.remove(src)
        time.sleep(2)
        author, lic = credit_for(filename)
        photos[lid] = {
            "hero": hero, "thumb": thumb, "author": author, "license": lic,
            "source": "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(filename.replace(" ", "_")),
            "wd": b["item"]["value"].split("/")[-1], "desc": b.get("desc", {}).get("value", ""),
        }
        json.dump(photos, open(out_path, "w"), indent=1, ensure_ascii=False)
        time.sleep(2.5)

    te = telugu_labels([p["wd"] for p in photos.values()])
    for p in photos.values():
        if p["wd"] in te:
            p["te"] = te[p["wd"]]
    json.dump(photos, open(out_path, "w"), indent=1, ensure_ascii=False)
    print(f"{len(photos)} photos, {sum(1 for p in photos.values() if p.get('te'))} Telugu names")


if __name__ == "__main__":
    main()
