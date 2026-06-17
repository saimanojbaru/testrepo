import type { Context, Config } from "@netlify/functions";
import { store, authAccount, makeCode, clean, clampNum, bad } from "./_lib.mts";

/* ═══════════════════════════════════════════════════
   AURA FARM — Multiplayer Challenges
   POST /api/challenge {action:"create", token, accountId, title, metric, target, days, name, avatar}
   POST /api/challenge {action:"join",     token, accountId, code, name, avatar}
   POST /api/challenge {action:"progress", token, accountId, code, progress}
   GET  /api/challenge?code=ABC123                 → live standings
   GET  /api/challenge?accountId=..&token=..&mine=1 → my challenges
   ═══════════════════════════════════════════════════ */

type Participant = { accountId: string; name: string; avatar: string; progress: number; joinedAt: number; updatedAt: number };
type Challenge = {
  code: string;
  ownerId: string;
  title: string;
  metric: string; // "aura" | "streak" | "taps"
  target: number;
  createdAt: number;
  expiresAt: number;
  participants: Record<string, Participant>;
};

const DAY = 86_400_000;

function view(ch: Challenge) {
  const standings = Object.values(ch.participants)
    .sort((a, b) => b.progress - a.progress || a.updatedAt - b.updatedAt)
    .map((p, i) => ({ rank: i + 1, name: p.name, avatar: p.avatar, progress: p.progress, accountId: p.accountId }));
  return {
    code: ch.code,
    title: ch.title,
    metric: ch.metric,
    target: ch.target,
    expiresAt: ch.expiresAt,
    active: Date.now() < ch.expiresAt,
    ownerId: ch.ownerId,
    standings,
  };
}

export default async (req: Request, _ctx: Context) => {
  const cs = store("aura-challenges");

  if (req.method === "GET") {
    const url = new URL(req.url);
    if (url.searchParams.get("mine")) {
      const acct = await authAccount(url.searchParams.get("accountId"), url.searchParams.get("token"));
      if (!acct) return bad("unauthorized", 401);
      const idx = ((await cs.get(`mine:${acct.accountId}`, { type: "json" })) || []) as string[];
      const out: any[] = [];
      for (const code of idx.slice(-20).reverse()) {
        const ch = (await cs.get(`ch:${code}`, { type: "json" })) as Challenge | null;
        if (ch) out.push(view(ch));
      }
      return Response.json({ challenges: out });
    }
    const code = clean(url.searchParams.get("code"), 8).toUpperCase();
    if (!code) return bad("missing code");
    const ch = (await cs.get(`ch:${code}`, { type: "json" })) as Challenge | null;
    if (!ch) return bad("no challenge with that code 🤨", 404);
    return Response.json(view(ch));
  }

  if (req.method !== "POST") return bad("method not allowed", 405);

  let body: any;
  try {
    body = await req.json();
  } catch {
    return bad("bad json");
  }

  const acct = await authAccount(body.accountId, body.token);
  if (!acct) return bad("unauthorized — log in to play multiplayer", 401);
  const action = clean(body.action, 12);
  const name = clean(body.name, 24) || acct.handle;
  const avatar = clean(body.avatar, 8) || "🧠";

  if (action === "create") {
    const metric = ["aura", "streak", "taps"].includes(body.metric) ? body.metric : "taps";
    const target = Math.max(1, clampNum(body.target, 1_000_000));
    const days = Math.min(90, Math.max(1, clampNum(body.days, 90) || 7));
    let code = makeCode();
    // avoid (rare) collision
    if (await cs.get(`ch:${code}`, { type: "json" })) code = makeCode();
    const ch: Challenge = {
      code,
      ownerId: acct.accountId,
      title: clean(body.title, 40) || "Aura Showdown",
      metric,
      target,
      createdAt: Date.now(),
      expiresAt: Date.now() + days * DAY,
      participants: {
        [acct.accountId]: { accountId: acct.accountId, name, avatar, progress: 0, joinedAt: Date.now(), updatedAt: Date.now() },
      },
    };
    await cs.setJSON(`ch:${code}`, ch);
    const mine = ((await cs.get(`mine:${acct.accountId}`, { type: "json" })) || []) as string[];
    mine.push(code);
    await cs.setJSON(`mine:${acct.accountId}`, mine);
    return Response.json(view(ch));
  }

  const code = clean(body.code, 8).toUpperCase();
  if (!code) return bad("missing code");
  const ch = (await cs.get(`ch:${code}`, { type: "json" })) as Challenge | null;
  if (!ch) return bad("no challenge with that code 🤨", 404);

  if (action === "join") {
    if (!ch.participants[acct.accountId]) {
      ch.participants[acct.accountId] = { accountId: acct.accountId, name, avatar, progress: 0, joinedAt: Date.now(), updatedAt: Date.now() };
      await cs.setJSON(`ch:${code}`, ch);
      const mine = ((await cs.get(`mine:${acct.accountId}`, { type: "json" })) || []) as string[];
      if (!mine.includes(code)) {
        mine.push(code);
        await cs.setJSON(`mine:${acct.accountId}`, mine);
      }
    }
    return Response.json(view(ch));
  }

  if (action === "progress") {
    const p = ch.participants[acct.accountId];
    if (!p) return bad("join the challenge first", 403);
    p.progress = clampNum(body.progress, 1_000_000_000);
    p.name = name;
    p.avatar = avatar;
    p.updatedAt = Date.now();
    await cs.setJSON(`ch:${code}`, ch);
    return Response.json(view(ch));
  }

  return bad("unknown action");
};

export const config: Config = {
  path: "/api/challenge",
};
