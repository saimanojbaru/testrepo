import type { GnmMarker } from '../ai/GnmMarkers';
import type { Gait } from '../ai/RealBody';

// Photoreal cast: which baked GNM head each character wears, their one cultural
// marker (plus glasses where they always had them), and a resting expression.
// Heads are synthetic identities from GNM's own identity model (no real people);
// region is carried by skin tone, grooming and the marker, never by caricature.
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

export const GNM_CAST: Record<string, GnmCastEntry> = {
  ramesh: { file: 'ramesh', markers: ['vibhuti', 'glassesGold'], body: 'ramesh', gait: { tempo: 0.9, lean: -0.06, arms: 0.7 } },
  kavita: { file: 'kavita', markers: ['chandrakor'], gait: { tempo: 1.12, lean: 0.03, arms: 0.8 }, rest: { smirk: 0.55, squint: 0.35 } },
  rinku: { file: 'rinku', markers: [], rest: { smile: 0.5, jawOpen: 0.28, browRaise: 0.3 } },
  priya: { file: 'priya', markers: ['chandanam', 'glassesBlack'] },
  rohit: { file: 'rohit', markers: [], gait: { tempo: 0.94, lean: -0.03, arms: 1.35 } },
  srinivas: { file: 'srinivas', markers: ['securityCap'] },
  lakshmi: { file: 'lakshmi', markers: ['bindi', 'noseRing'], gait: { tempo: 0.86, lean: 0.07, arms: 0.55 } },
  deepak: { file: 'deepak', markers: ['glassesBlack'], rest: { browRaise: 0.35 } },
  sanjay: { file: 'sanjay', markers: ['glassesSilver'] },
};
