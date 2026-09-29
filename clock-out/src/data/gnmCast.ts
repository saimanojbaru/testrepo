import type { GnmMarker } from '../ai/GnmMarkers';
import type { Gait } from '../ai/RealBody';
import { CAST, REALISTIC_HEAD_MARKERS, type Glasses } from './cast';

// Realistic (GNM) head config, derived from CAST. Do not add identity here: edit cast.ts.
export interface GnmCastEntry {
  file: string;
  markers: GnmMarker[];
  /** Realistic rigged body with mocap (public/anny/<body>.glb); without it the head sits on the low-poly body. */
  body?: string;
  /** Variation on the shared walk (see RealBody.Gait). */
  gait?: Gait;
  /** Morph weights held at rest (Rinku's open, eager face; Kavita's knowing smirk). */
  rest?: Record<string, number>;
}

const GLASSES: Record<Glasses, GnmMarker> = { gold: 'glassesGold', black: 'glassesBlack', silver: 'glassesSilver' };

export const GNM_CAST: Record<string, GnmCastEntry> = Object.fromEntries(Object.values(CAST).map((m) => {
  const markers = REALISTIC_HEAD_MARKERS.filter((k) => m.markers.includes(k)) as GnmMarker[];
  if (m.glasses) markers.push(GLASSES[m.glasses]);
  const entry: GnmCastEntry = { file: m.id, markers };
  if (m.realistic.body) entry.body = m.id;
  if (m.realistic.gait) entry.gait = m.realistic.gait;
  if (m.realistic.rest) entry.rest = m.realistic.rest;
  return [m.id, entry];
}));
