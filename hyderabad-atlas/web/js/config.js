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
