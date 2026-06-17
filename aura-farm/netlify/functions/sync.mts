import type { Context, Config } from "@netlify/functions";
import { store, authAccount, bad } from "./_lib.mts";

/* ═══════════════════════════════════════════════════
   AURA FARM — Cloud Save / Sync
   POST /api/sync {accountId, token, data, updatedAt}  → save
   GET  /api/sync?accountId=..&token=..                → load
   Stores the player's full game state so it persists
   across devices once they're logged in.
   ═══════════════════════════════════════════════════ */

const MAX_SAVE_BYTES = 256 * 1024; // 256KB ceiling on a save blob

export default async (req: Request, _ctx: Context) => {
  const saves = store("aura-saves");

  if (req.method === "GET") {
    const url = new URL(req.url);
    const acct = await authAccount(url.searchParams.get("accountId"), url.searchParams.get("token"));
    if (!acct) return bad("unauthorized", 401);
    const saved = await saves.get(`save:${acct.accountId}`, { type: "json" });
    return Response.json({ data: saved?.data ?? null, updatedAt: saved?.updatedAt ?? 0 });
  }

  if (req.method === "POST") {
    let body: any;
    try {
      body = await req.json();
    } catch {
      return bad("bad json");
    }
    const acct = await authAccount(body.accountId, body.token);
    if (!acct) return bad("unauthorized", 401);
    if (body.data == null || typeof body.data !== "object") return bad("missing data");

    const size = new TextEncoder().encode(JSON.stringify(body.data)).length;
    if (size > MAX_SAVE_BYTES) return bad("save too large", 413);

    const updatedAt = Date.now();
    await saves.setJSON(`save:${acct.accountId}`, { data: body.data, updatedAt });
    return Response.json({ ok: true, updatedAt });
  }

  return bad("method not allowed", 405);
};

export const config: Config = {
  path: "/api/sync",
};
