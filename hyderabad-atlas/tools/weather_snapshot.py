#!/usr/bin/env python3
"""Publish one shared weather file for the whole city.

At scale, phones should not each call the weather API: Hyderabad's weather is
the same for everyone. Run this every ~15 minutes (cron, GitHub Actions,
Cloud Run job...) and host the output on a CDN, then set
CONFIG.weather.sharedUrl in web/js/config.js to its URL.

    python3 tools/weather_snapshot.py out/weather.json
    OPEN_METEO_KEY=...  (optional, for the commercial Open-Meteo API)

Output format matches what the app builds itself in web/js/weather.js.
"""
import json
import math
import os
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ATLAS = os.path.join(HERE, "..", "web", "data", "atlas.json")
KEY = os.environ.get("OPEN_METEO_KEY")
FORECAST = "https://customer-api.open-meteo.com/v1/forecast" if KEY else "https://api.open-meteo.com/v1/forecast"
AIR = "https://customer-air-quality-api.open-meteo.com/v1/air-quality" if KEY else "https://air-quality-api.open-meteo.com/v1/air-quality"


def get(url, params):
    if KEY:
        params["apikey"] = KEY
    with urllib.request.urlopen(url + "?" + urllib.parse.urlencode(params, safe=","), timeout=60) as r:
        out = json.loads(r.read())
    return out if isinstance(out, list) else [out]


def main(path):
    atlas = json.load(open(ATLAS))
    pts = [(c["id"], c["label"][0], c["label"][1]) for c in atlas["circles"]]
    lat = ",".join(f"{p[2]:.3f}" for p in pts)
    lng = ",".join(f"{p[1]:.3f}" for p in pts)
    f = get(FORECAST, {"latitude": lat, "longitude": lng, "timezone": "Asia/Kolkata", "forecast_hours": 12,
                       "current": "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,is_day",
                       "hourly": "temperature_2m,precipitation_probability,precipitation,weather_code"})
    a = get(AIR, {"latitude": lat, "longitude": lng, "timezone": "Asia/Kolkata", "current": "us_aqi,pm2_5,pm10"})
    nx = ny = 7
    x0, y0, x1, y1 = 78.12, 17.08, 78.82, 17.72
    grid = [(x0 + (x1 - x0) * i / (nx - 1), y0 + (y1 - y0) * j / (ny - 1)) for j in range(ny) for i in range(nx)]
    w = get(FORECAST, {"latitude": ",".join(f"{g[1]:.3f}" for g in grid), "longitude": ",".join(f"{g[0]:.3f}" for g in grid),
                       "current": "wind_speed_10m,wind_direction_10m", "wind_speed_unit": "ms"})
    out = {"updated": datetime.now(timezone.utc).isoformat(), "source": "Open-Meteo", "circles": {}, "aqi": {},
           "wind": {"x0": x0, "y0": y0, "dx": (x1 - x0) / (nx - 1), "dy": (y1 - y0) / (ny - 1), "nx": nx, "ny": ny, "u": [], "v": []}}
    for (cid, _, _), r in zip(pts, f):
        c, h = r["current"], r["hourly"]
        out["circles"][cid] = {
            "t": c["temperature_2m"], "feels": c["apparent_temperature"], "rh": c["relative_humidity_2m"], "rain": c["precipitation"],
            "code": c["weather_code"], "cloud": c["cloud_cover"], "wind": c["wind_speed_10m"], "dir": c["wind_direction_10m"], "isDay": c["is_day"],
            "hourly": {"time": h["time"], "t": h["temperature_2m"], "pop": h["precipitation_probability"], "rain": h["precipitation"], "code": h["weather_code"]},
        }
    for (cid, _, _), r in zip(pts, a):
        out["aqi"][cid] = {"aqi": r["current"]["us_aqi"], "pm25": r["current"]["pm2_5"]}
    for r in w:
        s, d = r["current"]["wind_speed_10m"], math.radians(r["current"]["wind_direction_10m"])
        out["wind"]["u"].append(-s * math.sin(d))
        out["wind"]["v"].append(-s * math.cos(d))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(out, fh, separators=(",", ":"))
    print(f"wrote {path}: {len(out['circles'])} circles")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "weather.json")
