// The learning path. Each phase goes one level deeper; each lesson is a short
// guided flyover (tour) followed by a quiz built from the lesson's games.

const ZONE_ORDER = ['Charminar', 'Golconda', 'Khairatabad', 'Secunderabad', 'Rajendranagar', 'Shamshabad',
  'Kukatpally', 'Serilingampally', 'Qutbullapur', 'Malkajgiri', 'Uppal', 'LB Nagar'];

const ANCHORS = ['Hussain Sagar', 'Charminar', 'Golconda Fort', 'Cyber Towers', 'Secunderabad Junction',
  'Rajiv Gandhi Intl. Airport', 'Osmania University', 'Rajiv Gandhi Intl. Cricket Stadium'];

const LANDMARK_GROUPS = [
  ['Old City & heritage', ['heritage', 'museum', 'market']],
  ['Lakes & green spaces', ['lake', 'park']],
  ['City life', ['tech', 'education', 'sport', 'culture', 'temple', 'landmark']],
  ['Stations, buses & airports', ['transit']],
];

export function buildCurriculum(A) {
  const byName = (list, name) => list.find((x) => x.name === name);
  const zonesInOrder = ZONE_ORDER.map((n) => byName(A.zones, n));
  const phases = [];

  phases.push({
    id: 'p1', title: 'The big picture', sub: 'ORR, three corporations and the city\'s anchor points',
    lessons: [
      { id: 'corps', title: 'One city, three corporations', sub: 'GHMC · Cyberabad · Malkajgiri',
        items: A.corporations.map((c) => c.id), games: ['name', 'find', 'whichCorp', 'whichCorp'] },
      { id: 'anchors', title: 'Eight anchor points', sub: 'Landmarks to orient yourself',
        items: ANCHORS.map((n) => byName(A.landmarks, n)).filter(Boolean).map((m) => m.id), games: ['find', 'name', 'compass'] },
    ],
  });

  phases.push({
    id: 'p2', title: 'Twelve zones', sub: '6 in GHMC, 3 each in Cyberabad and Malkajgiri',
    lessons: A.corporations.map((c) => ({
      id: 'zones-' + c.id, title: `${c.name} zones`, sub: c.children.map((z) => A.get(z).name).join(' · '),
      items: c.children, games: ['name', 'find', 'neighbour', 'whichZone'],
    })),
  });

  phases.push({
    id: 'p3', title: 'Sixty circles', sub: 'Each zone splits into 4–7 circles',
    lessons: zonesInOrder.map((z) => ({
      id: 'circles-' + z.id, title: `${z.name} zone circles`, sub: z.children.map((c) => A.get(c).name).join(' · '),
      items: z.children, games: ['name', 'find', 'whichCircle', 'neighbour'],
    })),
  });

  phases.push({
    id: 'p4', title: 'Neighbourhoods', sub: 'Around 300 areas, zone by zone',
    lessons: zonesInOrder.map((z) => {
      const ids = z.children.flatMap((c) => A.get(c).children);
      return {
        id: 'areas-' + z.id, title: `Areas of ${z.name}`, sub: `${ids.length} areas in ${z.children.length} circles`,
        items: ids, games: ['findPoint', 'namePoint', 'whichCircle', 'compass'],
      };
    }),
  });

  phases.push({
    id: 'p5', title: 'Landmarks', sub: 'Heritage, lakes, campuses and transport hubs',
    lessons: LANDMARK_GROUPS.map(([title, cats], i) => {
      const ids = A.landmarks.filter((m) => cats.includes(m.cat)).map((m) => m.id);
      return { id: 'lm-' + i, title, sub: `${ids.length} places`, items: ids, games: ['findPoint', 'namePoint', 'whichCorp', 'compass'] };
    }),
  });

  phases.push({
    id: 'p6', title: 'Metro', sub: 'Red, Blue and Green lines, station by station',
    lessons: [
      ...A.metro.map((l) => ({
        id: 'metro-' + l.id, title: l.name, sub: `${l.stationIds.length} stations · ${A.get(l.stationIds[0]).name} → ${A.get(l.stationIds.at(-1)).name}`,
        items: l.stationIds, line: l.id, games: ['nextStop', 'order', 'findStation', 'lineOf'],
        tour: keyStations(A, l),
      })),
      { id: 'metro-mix', title: 'Interchanges', sub: 'Where the lines meet',
        items: A.interchanges.map((s) => s.id).concat(A.metro.flatMap((l) => [l.stationIds[0], l.stationIds.at(-1)])),
        games: ['interchange', 'lineOf', 'findStation', 'order'] },
    ],
  });

  phases.push({
    id: 'p7', title: 'Routes', sub: 'Highways and corridors, in order',
    lessons: [
      { id: 'routes-out', title: 'Highways out of the city', sub: 'NH44, NH65 (both ways), NH163',
        items: A.corridors.slice(0, 4).map((r) => r.id), games: ['order', 'whichRoad', 'order'] },
      { id: 'routes-in', title: 'Airport road & IT corridor', sub: 'PVNR Expressway · Panjagutta → Financial District',
        items: A.corridors.slice(4).map((r) => r.id), games: ['order', 'whichRoad', 'order'] },
    ],
  });

  phases.forEach((p, pi) => p.lessons.forEach((l, li) => { l.phase = p.id; l.phaseIndex = pi; l.index = li; }));
  return phases;
}

function keyStations(A, line) {
  const ids = line.stationIds;
  const out = new Set([ids[0], ids.at(-1)]);
  ids.forEach((id, i) => { if (A.get(id).lines.length > 1 || i % 4 === 0) out.add(id); });
  return ids.filter((id) => out.has(id));
}

export function allLessons(phases) { return phases.flatMap((p) => p.lessons); }
