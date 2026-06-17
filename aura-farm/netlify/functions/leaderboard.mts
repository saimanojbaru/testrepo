import type { Context, Config } from "@netlify/functions";
import { getStore } from "@netlify/blobs";

/* ═══════════════════════════════════════════════════
   AURA FARM — Global Leaderboard (Netlify Function + Blobs)
   GET    /api/leaderboard            → top players by aura
   POST   /api/leaderboard {id,...}   → upsert your score, returns rank
   POST   /api/leaderboard {id,remove}→ remove yourself from the board
   ═══════════════════════════════════════════════════ */

const TOP_N = 50;        // how many we return to clients
const MAX_ENTRIES = 1000; // hard cap on stored players to bound the blob size
const BOARD_KEY = "board";

type Entry = {
  id: string;
  name: string;
  aura: number;
  level: number;
  levelName: string;
  icon: string;
  avatar: string;
  streak: number;
  updatedAt: number;
};

// keep prod data isolated from preview/branch deploys
function board() {
  const ctx = (globalThis as any).Netlify?.env?.get("CONTEXT") || "dev";
  const name = ctx === "production" ? "aura-leaderboard" : `aura-leaderboard-${ctx}`;
  return getStore({ name, consistency: "strong" });
}

const clean = (v: unknown, max: number) =>
  String(v ?? "").replace(/[<>]/g, "").replace(/[\u0000-\u001f]/g, "").trim().slice(0, max);
const clampNum = (v: unknown, max: number) => {
  const n = Math.floor(Number(v));
  return Number.isFinite(n) && n >= 0 ? Math.min(n, max) : 0;
};
const clampInt = (v: unknown, min: number, max: number) => {
  const n = Math.floor(Number(v));
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : min;
};

const sortByAura = (a: Entry, b: Entry) => b.aura - a.aura || a.updatedAt - b.updatedAt;

export default async (req: Request, _ctx: Context) => {
  const store = board();

  if (req.method === "GET") {
    const data = ((await store.get(BOARD_KEY, { type: "json" })) || {}) as Record<string, Entry>;
    const all = Object.values(data).sort(sortByAura);
    return Response.json({ top: all.slice(0, TOP_N), total: all.length });
  }

  if (req.method === "POST") {
    let body: any;
    try {
      body = await req.json();
    } catch {
      return new Response("bad json", { status: 400 });
    }

    const id = clean(body.id, 40);
    if (!id) return new Response("missing id", { status: 400 });

    const data = ((await store.get(BOARD_KEY, { type: "json" })) || {}) as Record<string, Entry>;

    if (body.remove) {
      delete data[id];
      await store.setJSON(BOARD_KEY, data);
      return Response.json({ removed: true, total: Object.keys(data).length });
    }

    const entry: Entry = {
      id,
      name: clean(body.name, 24) || "Anon",
      aura: clampNum(body.aura, 1_000_000_000),
      level: clampInt(body.level, 1, 99),
      levelName: clean(body.levelName, 32),
      icon: clean(body.icon, 8) || "🫥",
      avatar: clean(body.avatar, 8) || "🧠",
      streak: clampNum(body.streak, 100_000),
      updatedAt: Date.now(),
    };
    data[id] = entry;

    // bound storage: keep only the top MAX_ENTRIES (plus the current player)
    let entries = Object.values(data);
    if (entries.length > MAX_ENTRIES) {
      const kept = entries.sort(sortByAura).slice(0, MAX_ENTRIES);
      const trimmed: Record<string, Entry> = {};
      for (const e of kept) trimmed[e.id] = e;
      trimmed[id] = entry;
      await store.setJSON(BOARD_KEY, trimmed);
      entries = Object.values(trimmed);
    } else {
      await store.setJSON(BOARD_KEY, data);
    }

    const ranked = entries.sort(sortByAura);
    const rank = ranked.findIndex((e) => e.id === id) + 1;
    return Response.json({ rank, total: ranked.length });
  }

  return new Response("method not allowed", { status: 405 });
};

export const config: Config = {
  path: "/api/leaderboard",
};
