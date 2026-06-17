import type { Context, Config } from "@netlify/functions";
import { store, hashPin, safeEqual, makeToken, makeId, clean, bad } from "./_lib.mts";

/* ═══════════════════════════════════════════════════
   AURA FARM — Accounts
   POST /api/account {action:"signup", handle, pin}
   POST /api/account {action:"login",  handle, pin}
   → { accountId, token, handle }
   Credentials are PBKDF2-hashed; tokens are opaque session keys.
   ═══════════════════════════════════════════════════ */

type Account = {
  accountId: string;
  handle: string;
  salt: string;
  hash: string;
  token: string;
  createdAt: number;
};

const validHandle = (h: string) => /^[a-zA-Z0-9_]{3,20}$/.test(h);

export default async (req: Request, _ctx: Context) => {
  if (req.method !== "POST") return bad("method not allowed", 405);

  let body: any;
  try {
    body = await req.json();
  } catch {
    return bad("bad json");
  }

  const action = clean(body.action, 12);
  const handle = clean(body.handle, 20);
  const pin = String(body.pin ?? "");
  const handleLower = handle.toLowerCase();

  if (!validHandle(handle)) return bad("handle must be 3-20 letters, numbers or _");
  if (pin.length < 4 || pin.length > 64) return bad("pin must be 4-64 characters");

  const accounts = store("aura-accounts");
  const idxKey = `handle:${handleLower}`;

  if (action === "signup") {
    const existing = await accounts.get(idxKey, { type: "text" });
    if (existing) return bad("that handle is taken, pick another 💀", 409);

    const { salt, hash } = await hashPin(pin);
    const accountId = makeId("acct");
    const token = makeToken();
    const acct: Account = { accountId, handle, salt, hash, token, createdAt: Date.now() };

    await accounts.setJSON(`acct:${accountId}`, acct);
    await accounts.set(idxKey, accountId);
    return Response.json({ accountId, token, handle });
  }

  if (action === "login") {
    const accountId = await accounts.get(idxKey, { type: "text" });
    if (!accountId) return bad("no account with that handle 🤨", 404);
    const acct = (await accounts.get(`acct:${accountId}`, { type: "json" })) as Account | null;
    if (!acct) return bad("account data missing", 404);

    const { hash } = await hashPin(pin, acct.salt);
    if (!safeEqual(hash, acct.hash)) return bad("wrong pin, try again 💀", 401);

    // rotate token on each login
    acct.token = makeToken();
    await accounts.setJSON(`acct:${accountId}`, acct);
    return Response.json({ accountId: acct.accountId, token: acct.token, handle: acct.handle });
  }

  return bad("unknown action");
};

export const config: Config = {
  path: "/api/account",
};
