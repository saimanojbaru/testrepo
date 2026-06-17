import { getStore } from "@netlify/blobs";

/* shared helpers for Aura Farm backend functions.
   files prefixed with "_" are not treated as endpoints by Netlify,
   they're only bundled when imported. */

// keep production data isolated from preview/branch deploys
export function store(base: string) {
  const ctx = (globalThis as any).Netlify?.env?.get("CONTEXT") || "dev";
  const name = ctx === "production" ? base : `${base}-${ctx}`;
  return getStore({ name, consistency: "strong" });
}

const C = (globalThis as any).crypto as Crypto;

export function bytesToHex(b: Uint8Array) {
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}
export function hexToBytes(h: string) {
  const out = new Uint8Array(h.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(h.substr(i * 2, 2), 16);
  return out;
}
export function makeToken() {
  return bytesToHex(C.getRandomValues(new Uint8Array(24)));
}
export function makeId(prefix = "a") {
  return prefix + "_" + bytesToHex(C.getRandomValues(new Uint8Array(9)));
}
// short shareable challenge code, no ambiguous chars
export function makeCode() {
  const alpha = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  const r = C.getRandomValues(new Uint8Array(6));
  return [...r].map((x) => alpha[x % alpha.length]).join("");
}

// PBKDF2 password/pin hashing via Web Crypto (no deps)
export async function hashPin(pin: string, saltHex?: string) {
  const enc = new TextEncoder();
  const salt = saltHex ? hexToBytes(saltHex) : C.getRandomValues(new Uint8Array(16));
  const key = await C.subtle.importKey("raw", enc.encode(pin), "PBKDF2", false, ["deriveBits"]);
  const bits = await C.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: 100_000, hash: "SHA-256" },
    key,
    256
  );
  return { salt: bytesToHex(salt), hash: bytesToHex(new Uint8Array(bits)) };
}
// constant-time-ish compare
export function safeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export const clean = (v: unknown, max: number) =>
  String(v ?? "").replace(/[<>]/g, "").replace(/[\u0000-\u001f]/g, "").trim().slice(0, max);
export const clampNum = (v: unknown, max: number) => {
  const n = Math.floor(Number(v));
  return Number.isFinite(n) && n >= 0 ? Math.min(n, max) : 0;
};

export const bad = (msg: string, code = 400) => new Response(msg, { status: code });

// verify {accountId, token} against the accounts store; returns the account or null
export async function authAccount(accountId: unknown, token: unknown) {
  const id = clean(accountId, 40);
  const tok = clean(token, 80);
  if (!id || !tok) return null;
  const accounts = store("aura-accounts");
  const acct = (await accounts.get(`acct:${id}`, { type: "json" })) as any;
  if (!acct || !safeEqual(String(acct.token), tok)) return null;
  return acct as { accountId: string; handle: string; token: string };
}
