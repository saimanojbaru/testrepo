import type { GnmMarker } from '../ai/GnmMarkers';

// Photoreal cast: which baked GNM head each character wears, their one cultural
// marker (plus glasses where they always had them), and a resting expression.
// Heads are synthetic identities from GNM's own identity model (no real people);
// region is carried by skin tone, grooming and the marker, never by caricature.
export interface GnmCastEntry {
  file: string;
  markers: GnmMarker[];
  /** Morph weights held at rest (Rinku's open, eager face; Kavita's knowing smirk). */
  rest?: Record<string, number>;
}

export const GNM_CAST: Record<string, GnmCastEntry> = {
  ramesh: { file: 'ramesh', markers: ['vibhuti', 'glassesGold'] },
  kavita: { file: 'kavita', markers: ['chandrakor'], rest: { smirk: 0.55, squint: 0.35 } },
  rinku: { file: 'rinku', markers: [], rest: { smile: 0.5, jawOpen: 0.28, browRaise: 0.3 } },
  priya: { file: 'priya', markers: ['chandanam', 'glassesBlack'] },
  rohit: { file: 'rohit', markers: [] },
  srinivas: { file: 'srinivas', markers: ['securityCap'] },
  lakshmi: { file: 'lakshmi', markers: ['bindi', 'noseRing'] },
  deepak: { file: 'deepak', markers: ['glassesBlack'], rest: { browRaise: 0.35 } },
  sanjay: { file: 'sanjay', markers: ['glassesSilver'] },
};
