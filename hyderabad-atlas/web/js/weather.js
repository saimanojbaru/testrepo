// Weather for every circle: current conditions + next 12 hours, air quality,
// and a wind grid for the animated wind streaks.
//
// Two sources (see CONFIG.weather):
//  - shared: one JSON file published every ~15 min by tools/weather_snapshot.py
//    (use this at scale: every phone downloads the same small file)
//  - direct: each phone asks Open-Meteo itself (fine for testing / small scale)
import { CONFIG } from './config.js';

const CACHE_KEY = 'hyd-wx-v1';
const MAX_AGE = 15 * 60e3;

export const WX_CODES = {
  0: ['Clear', '☀️'], 1: ['Mainly clear', '🌤️'], 2: ['Partly cloudy', '⛅'], 3: ['Overcast', '☁️'],
  45: ['Fog', '🌫️'], 48: ['Fog', '🌫️'], 51: ['Light drizzle', '🌦️'], 53: ['Drizzle', '🌦️'], 55: ['Heavy drizzle', '🌧️'],
  61: ['Light rain', '🌦️'], 63: ['Rain', '🌧️'], 65: ['Heavy rain', '🌧️'], 80: ['Showers', '🌦️'], 81: ['Showers', '🌧️'],
  82: ['Violent showers', '⛈️'], 95: ['Thunderstorm', '⛈️'], 96: ['Thunderstorm, hail', '⛈️'], 99: ['Thunderstorm, hail', '⛈️'],
};
export const wxText = (code) => (WX_CODES[code] || ['—', ''])[0];
export const wxIcon = (code) => (WX_CODES[code] || ['', '🌡️'])[1];

// colour scales per metric: [value, colour] stops
export const METRICS = {
  temp: { label: 'Temp', unit: '°', stops: [[18, '#3b82f6'], [24, '#22c1c3'], [28, '#5be49b'], [32, '#f2cf4a'], [36, '#f97316'], [40, '#ef4444']] },
  rain: { label: 'Rain', unit: '%', stops: [[0, '#1f2937'], [20, '#1e3a8a'], [40, '#2563eb'], [60, '#38bdf8'], [80, '#a78bfa'], [100, '#f0abfc']] },
  wind: { label: 'Wind', unit: ' km/h', stops: [[0, '#1f2937'], [6, '#0e7490'], [12, '#22c1c3'], [20, '#5be49b'], [30, '#f2cf4a'], [45, '#ef4444']] },
  aqi: { label: 'AQI', unit: '', stops: [[0, '#22c55e'], [50, '#84cc16'], [100, '#eab308'], [150, '#f97316'], [200, '#ef4444'], [300, '#7e22ce']] },
};

export function metricValue(w, aqi, metric) {
  if (!w) return null;
  if (metric === 'temp') return w.t;
  if (metric === 'rain') return Math.max(...(w.hourly.pop || [0]).slice(0, 3));
  if (metric === 'wind') return w.wind;
  if (metric === 'aqi') return aqi ? aqi.aqi : null;
  return null;
}

const join = (arr, k) => arr.map((p) => p[k].toFixed(3)).join(',');

function gridPoints(nx = 7, ny = 7) {
  const [x0, y0, x1, y1] = [78.12, 17.08, 78.82, 17.72];
  const pts = [];
  for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) pts.push({ lng: x0 + ((x1 - x0) * i) / (nx - 1), lat: y0 + ((y1 - y0) * j) / (ny - 1) });
  return { pts, grid: { x0, y0, dx: (x1 - x0) / (nx - 1), dy: (y1 - y0) / (ny - 1), nx, ny } };
}

// Ask Open-Meteo directly for all 60 circles + air quality + a wind grid.
async function fetchDirect(A) {
  const pts = A.circles.map((c) => ({ id: c.id, lng: c.label[0], lat: c.label[1] }));
  const base = CONFIG.weather.openMeteo;
  const fq = `${base.forecast}?latitude=${join(pts, 'lat')}&longitude=${join(pts, 'lng')}`
    + '&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,is_day'
    + '&hourly=temperature_2m,precipitation_probability,precipitation,weather_code&forecast_hours=12&timezone=Asia%2FKolkata';
  const aq = `${base.air}?latitude=${join(pts, 'lat')}&longitude=${join(pts, 'lng')}&current=us_aqi,pm2_5,pm10&timezone=Asia%2FKolkata`;
  const { pts: gp, grid } = gridPoints();
  const wq = `${base.forecast}?latitude=${join(gp, 'lat')}&longitude=${join(gp, 'lng')}&current=wind_speed_10m,wind_direction_10m&wind_speed_unit=ms`;
  const [f, a, w] = await Promise.all([fq, aq, wq].map((u) => fetch(u).then((r) => (r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status))))));
  const arr = (x) => (Array.isArray(x) ? x : [x]);
  const out = { updated: new Date().toISOString(), source: 'Open-Meteo', circles: {}, aqi: {}, wind: { ...grid, u: [], v: [] } };
  arr(f).forEach((r, i) => {
    const c = r.current, h = r.hourly;
    out.circles[pts[i].id] = {
      t: c.temperature_2m, feels: c.apparent_temperature, rh: c.relative_humidity_2m, rain: c.precipitation,
      code: c.weather_code, cloud: c.cloud_cover, wind: c.wind_speed_10m, dir: c.wind_direction_10m, isDay: c.is_day,
      hourly: { time: h.time, t: h.temperature_2m, pop: h.precipitation_probability, rain: h.precipitation, code: h.weather_code },
    };
  });
  arr(a).forEach((r, i) => { out.aqi[pts[i].id] = { aqi: r.current.us_aqi, pm25: r.current.pm2_5 }; });
  arr(w).forEach((r) => {
    const s = r.current.wind_speed_10m, d = (r.current.wind_direction_10m * Math.PI) / 180;
    out.wind.u.push(-s * Math.sin(d));   // wind blows FROM `dir`
    out.wind.v.push(-s * Math.cos(d));
  });
  return out;
}

export async function loadWeather(A, { force = false } = {}) {
  try {
    const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
    if (!force && cached && Date.now() - new Date(cached.fetchedAt).getTime() < MAX_AGE) return cached;
  } catch { /* ignore */ }
  let data;
  if (CONFIG.weather.sharedUrl) {
    const r = await fetch(CONFIG.weather.sharedUrl, { cache: 'no-cache' });
    if (!r.ok) throw new Error('weather file HTTP ' + r.status);
    data = await r.json();
  } else {
    data = await fetchDirect(A);
  }
  data.fetchedAt = new Date().toISOString();
  try { localStorage.setItem(CACHE_KEY, JSON.stringify(data)); } catch { /* storage full */ }
  return data;
}

export function cachedWeather() {
  try { return JSON.parse(localStorage.getItem(CACHE_KEY) || 'null'); } catch { return null; }
}

// Plain-language insights that point at circles on the map.
export function insights(A, wx) {
  const out = [];
  const rows = A.circles.map((c) => ({ c, w: wx.circles[c.id], a: wx.aqi[c.id] })).filter((r) => r.w);
  if (!rows.length) return out;
  const rainSoon = rows.filter((r) => Math.max(...r.w.hourly.pop.slice(0, 3)) >= 50).sort((a, b) => Math.max(...b.w.hourly.pop.slice(0, 3)) - Math.max(...a.w.hourly.pop.slice(0, 3)));
  const raining = rows.filter((r) => r.w.rain > 0.1);
  if (raining.length) out.push({ icon: '🌧️', title: `Raining now in ${raining.length} circle${raining.length > 1 ? 's' : ''}`, text: raining.slice(0, 4).map((r) => r.c.name).join(', '), id: raining[0].c.id, tone: 'rain' });
  if (rainSoon.length) out.push({ icon: '☔', title: 'Rain likely in the next 3 hours', text: rainSoon.slice(0, 5).map((r) => r.c.name).join(', ') + (rainSoon.length > 5 ? ` +${rainSoon.length - 5}` : ''), id: rainSoon[0].c.id, tone: 'rain' });
  else out.push({ icon: '🌂', title: 'No rain expected for 3 hours', text: 'Across all 60 circles', tone: 'calm' });
  const hot = rows.reduce((a, b) => (b.w.t > a.w.t ? b : a));
  const cool = rows.reduce((a, b) => (b.w.t < a.w.t ? b : a));
  out.push({ icon: '🔥', title: `Hottest: ${hot.c.name} ${Math.round(hot.w.t)}°`, text: `${A.get(hot.c.zone).name} zone · feels ${Math.round(hot.w.feels)}°`, id: hot.c.id, tone: 'hot' });
  if (hot.w.t - cool.w.t >= 0.5) out.push({ icon: '🍃', title: `Coolest: ${cool.c.name} ${Math.round(cool.w.t)}°`, text: `${(hot.w.t - cool.w.t).toFixed(1)}° cooler than ${hot.c.name}`, id: cool.c.id, tone: 'cool' });
  const windy = rows.reduce((a, b) => (b.w.wind > a.w.wind ? b : a));
  out.push({ icon: '💨', title: `Windiest: ${windy.c.name} ${Math.round(windy.w.wind)} km/h`, text: `From the ${compass16(windy.w.dir)}`, id: windy.c.id, tone: 'wind' });
  const aq = rows.filter((r) => r.a && r.a.aqi != null);
  if (aq.length) {
    const worst = aq.reduce((a, b) => (b.a.aqi > a.a.aqi ? b : a));
    const best = aq.reduce((a, b) => (b.a.aqi < a.a.aqi ? b : a));
    out.push({ icon: '😷', title: `Worst air: ${worst.c.name} AQI ${Math.round(worst.a.aqi)}`, text: `${aqiLabel(worst.a.aqi)} · PM2.5 ${Math.round(worst.a.pm25)}`, id: worst.c.id, tone: 'aqi' });
    out.push({ icon: '🌿', title: `Cleanest air: ${best.c.name} AQI ${Math.round(best.a.aqi)}`, text: aqiLabel(best.a.aqi), id: best.c.id, tone: 'calm' });
  }
  const avgCloud = rows.reduce((s, r) => s + r.w.cloud, 0) / rows.length;
  out.push({ icon: '☁️', title: `${Math.round(avgCloud)}% cloud over the city`, text: 'Turn on the satellite layer to see the clouds', tone: 'calm' });
  return out;
}

export function cityNow(A, wx) {
  const rows = A.circles.map((c) => wx.circles[c.id]).filter(Boolean);
  if (!rows.length) return null;
  const avg = (k) => rows.reduce((s, r) => s + r[k], 0) / rows.length;
  const codes = rows.map((r) => r.code);
  const mode = codes.sort((a, b) => codes.filter((v) => v === a).length - codes.filter((v) => v === b).length).pop();
  const aqs = Object.values(wx.aqi || {}).map((a) => a.aqi).filter((v) => v != null);
  return { t: avg('t'), feels: avg('feels'), rh: avg('rh'), wind: avg('wind'), code: mode, aqi: aqs.length ? aqs.reduce((a, b) => a + b, 0) / aqs.length : null };
}

export function aqiLabel(v) {
  if (v == null) return '';
  return v <= 50 ? 'Good' : v <= 100 ? 'Moderate' : v <= 150 ? 'Unhealthy for sensitive groups' : v <= 200 ? 'Unhealthy' : v <= 300 ? 'Very unhealthy' : 'Hazardous';
}

export function compass16(deg) {
  const n = ['north', 'north-north-east', 'north-east', 'east-north-east', 'east', 'east-south-east', 'south-east', 'south-south-east', 'south', 'south-south-west', 'south-west', 'west-south-west', 'west', 'west-north-west', 'north-west', 'north-north-west'];
  return n[Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
}

// Zoom Earth deep link (format read from zoom.earth's own app code).
export function zoomEarthUrl(lng, lat, zoom, metric) {
  const layer = { temp: 'temperature', rain: 'radar', wind: 'wind-speed', aqi: 'satellite', clouds: 'satellite' }[metric] || 'satellite';
  return `https://zoom.earth/maps/${layer}/#view=${lat.toFixed(3)},${lng.toFixed(3)},${Math.max(3, Math.min(12, Math.round(zoom)))}z`;
}

// ------------------------------------------------------------------ wind streaks
// Particles drift through the wind grid and leave fading trails, Zoom Earth style.
export class WindLayer {
  constructor(map, canvas) {
    this.map = map;
    this.cv = canvas;
    this.ctx = canvas.getContext('2d');
    this.running = false;
    this.field = null;
    this.parts = [];
    this.map.on('movestart', () => this.clear());
    this.map.on('resize', () => this.resize());
  }

  setField(f) { this.field = f; this.parts = []; }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.cv.width = this.cv.clientWidth * dpr;
    this.cv.height = this.cv.clientHeight * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  clear() { this.ctx.clearRect(0, 0, this.cv.width, this.cv.height); }

  sample(lng, lat) {
    const f = this.field;
    const gx = (lng - f.x0) / f.dx, gy = (lat - f.y0) / f.dy;
    if (gx < 0 || gy < 0 || gx > f.nx - 1 || gy > f.ny - 1) return null;
    const i = Math.min(f.nx - 2, Math.floor(gx)), j = Math.min(f.ny - 2, Math.floor(gy));
    const fx = gx - i, fy = gy - j;
    const at = (a, ii, jj) => a[jj * f.nx + ii];
    const lerp = (a) => (at(a, i, j) * (1 - fx) + at(a, i + 1, j) * fx) * (1 - fy) + (at(a, i, j + 1) * (1 - fx) + at(a, i + 1, j + 1) * fx) * fy;
    return [lerp(f.u), lerp(f.v)];
  }

  spawn() {
    const f = this.field;
    const b = this.map.getBounds();
    const x0 = Math.max(f.x0, b.getWest()), x1 = Math.min(f.x0 + f.dx * (f.nx - 1), b.getEast());
    const y0 = Math.max(f.y0, b.getSouth()), y1 = Math.min(f.y0 + f.dy * (f.ny - 1), b.getNorth());
    if (x1 <= x0 || y1 <= y0) return null;
    return { lng: x0 + Math.random() * (x1 - x0), lat: y0 + Math.random() * (y1 - y0), age: Math.random() * 80 };
  }

  start() {
    if (this.running || !this.field) return;
    this.running = true;
    this.resize();
    const N = window.innerWidth < 500 ? 700 : 1200;
    const loop = () => {
      if (!this.running) return;
      const ctx = this.ctx, W = this.cv.clientWidth, H = this.cv.clientHeight;
      ctx.globalCompositeOperation = 'destination-in';
      ctx.fillStyle = 'rgba(0,0,0,0.92)';
      ctx.fillRect(0, 0, W, H);
      ctx.globalCompositeOperation = 'source-over';
      while (this.parts.length < N) { const p = this.spawn(); if (!p) break; this.parts.push(p); }
      const k = 0.00006 * Math.pow(2, 10 - this.map.getZoom());
      ctx.lineWidth = 1.2;
      ctx.strokeStyle = 'rgba(235,245,255,0.75)';
      ctx.beginPath();
      for (let i = 0; i < this.parts.length; i++) {
        const p = this.parts[i];
        const uv = this.sample(p.lng, p.lat);
        if (!uv || p.age > 90) { const q = this.spawn(); if (q) { q.age = 0; this.parts[i] = q; } continue; }
        const a = this.map.project([p.lng, p.lat]);
        p.lng += (uv[0] * k * 1.0) / Math.cos((p.lat * Math.PI) / 180);
        p.lat += uv[1] * k;
        p.age++;
        const b = this.map.project([p.lng, p.lat]);
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
      }
      ctx.stroke();
      this.raf = requestAnimationFrame(loop);
    };
    this.raf = requestAnimationFrame(loop);
  }

  stop() {
    this.running = false;
    cancelAnimationFrame(this.raf);
    this.clear();
  }
}
