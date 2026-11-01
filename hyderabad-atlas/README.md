# Hyderabad Atlas

An Android app for learning Hyderabad's geography layer by layer, on a 3D satellite map styled after the realtor's pov reel about the 2026 three-corporation split.

![Hyderabad Atlas screens](docs/preview.jpg)

**Install:** download [`release/HyderabadAtlas.apk`](release/HyderabadAtlas.apk) on your phone, open it, and allow "Install unknown apps" for your browser or file manager when asked. Android 8.0 or newer. You need internet for the satellite imagery.

## What's inside

| Level | Count | What you learn |
|---|---|---|
| Corporations | 3 | GHMC, Cyberabad, Malkajgiri (G.O. 55, 11 Feb 2026) |
| Zones | 12 | 6 GHMC + 3 Cyberabad + 3 Malkajgiri |
| Circles | 60 | official circle list with ward counts |
| Areas | 303 | neighbourhoods, each placed in its circle |
| Landmarks | 48 | heritage, lakes, campuses, transit hubs |
| Metro | 3 lines, 57 stations | station order, interchanges |
| Routes | 6 corridors | NH44, NH65 both ways, NH163, airport road, IT corridor |

**Explore:** pick a depth (Corporations → Zones → Circles → Areas → Landmarks → Metro → Routes) and tap anything for its parent hierarchy, neighbours, contents, nearest metro and nearby landmarks.

**Learn:** 7 phases, 39 lessons. Each lesson is a guided flyover, then a quiz. Score 70% to unlock the next lesson.

**Play:** 9 game modes:
- Pin drop
- Name it
- "Mee area ye corporation?"
- Compass
- Neighbours
- Metro master
- Route builder
- Daily mix
- Fix my mistakes

Every answer feeds a spaced-repetition memory (Leitner boxes), so weak places keep coming back. XP, a daily streak and per-level mastery are tracked on the Progress tab.

## Accuracy

- **Hierarchy (corporation, zone, circle, ward count):** from the official February 2026 lists published by Siasat and Telangana Today.
- **Area positions, metro and ORR:** from OpenStreetMap.
- **Borders are approximate:** no official boundary file exists yet for the new circles. Each area gets a Voronoi cell around its OSM position, and cells merge into circles, zones and corporations, so the levels always nest. The assignment of areas to circles is hand-curated and may be wrong at the edges.
- **Route corridors:** drawn as straight lines between stops (a schematic). Turn on the road overlay to see the real roads.

## Project layout

```
web/            the app (HTML/CSS/JS, MapLibre GL, data/atlas.json)
tools/          data pipeline: hierarchy.py (curated lists) + build_data.py
tools/raw/      OpenStreetMap extracts used by the pipeline
android/        WebView shell that packages web/ as assets
release/        signed APK
```

## Rebuild

```bash
# data (only if you edit tools/hierarchy.py)
pip install shapely numpy
python3 tools/build_data.py

# try the app in a browser
cd web && python3 -m http.server 8000   # open http://localhost:8000

# Android APK (needs JDK 17 + Android SDK 35)
cd android && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ../release/HyderabadAtlas.apk
```

The signing key in `android/keystore/` exists so every rebuild can update the installed app in place. Uninstalling would wipe your progress. It's a sideloading key only; don't reuse it for a Play Store release.

## Credits

Imagery © Esri, Maxar, Earthstar Geographics. Map data © OpenStreetMap contributors (ODbL). Map engine: MapLibre GL JS (BSD-3). Font: Inter (OFL).
