// Map data providers. Change these in one place when moving to a paid or
// self-hosted provider (needed before a large public launch, see README).
export const CONFIG = {
  satellite: {
    tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
    maxzoom: 19,
    attribution: 'Imagery © Esri, Maxar, Earthstar Geographics',
  },
  // OpenMapTiles-schema vector tiles: 3D buildings, roads, road names
  vector: {
    url: 'https://tiles.openfreemap.org/planet',
    glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
    attribution: '© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors',
  },
  // Weather. Leave sharedUrl empty to let each phone call Open-Meteo directly
  // (testing / small scale). At scale, publish one file every 15 minutes with
  // tools/weather_snapshot.py and put its URL here.
  weather: {
    sharedUrl: '',
    openMeteo: {
      forecast: 'https://api.open-meteo.com/v1/forecast',
      air: 'https://air-quality-api.open-meteo.com/v1/air-quality',
    },
    // live clouds: NASA GIBS, Himawari infrared, refreshed every 10 minutes
    clouds: 'https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/Himawari_AHI_Band13_Clean_Infrared/default/default/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png',
    // rain radar frames list (RainViewer; free tier: zoom <= 7, personal/small use)
    radarIndex: 'https://api.rainviewer.com/public/weather-maps.json',
  },
  // Shared poll results for the Gen Z column (off until a backend exists)
  community: { url: '' },
  // Terrarium-encoded elevation (AWS Open Data terrain tiles)
  terrain: {
    tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
    maxzoom: 13,
    exaggeration: 1.5,
  },
};

// Rough low-end device check: these phones get "lite" mode by default
// (no terrain, no 3D buildings, lower render resolution).
export function isLowEndDevice() {
  const mem = navigator.deviceMemory || 4;
  const cores = navigator.hardwareConcurrency || 4;
  return mem <= 3 || cores <= 4;
}
