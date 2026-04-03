import fs from "fs";
import path from "path";
import os from "os";

const CCPI_DIR = path.join(os.homedir(), ".ccpi");
const STORE_FILE = path.join(CCPI_DIR, "installed.json");

export interface InstalledPlugin {
  name: string;
  version: string;
  description: string;
  author: string;
  channel: string;
  skillPath?: string;
  installedAt: string;
}

function ensureDir(): void {
  if (!fs.existsSync(CCPI_DIR)) {
    fs.mkdirSync(CCPI_DIR, { recursive: true });
  }
}

export function readStore(): InstalledPlugin[] {
  ensureDir();
  if (!fs.existsSync(STORE_FILE)) return [];
  try {
    return JSON.parse(fs.readFileSync(STORE_FILE, "utf-8")) as InstalledPlugin[];
  } catch {
    return [];
  }
}

export function writeStore(plugins: InstalledPlugin[]): void {
  ensureDir();
  fs.writeFileSync(STORE_FILE, JSON.stringify(plugins, null, 2), "utf-8");
}

export function addToStore(plugin: Omit<InstalledPlugin, "installedAt">): void {
  const store = readStore();
  const existing = store.findIndex((p) => p.name === plugin.name);
  const entry: InstalledPlugin = { ...plugin, installedAt: new Date().toISOString() };
  if (existing !== -1) {
    store[existing] = entry;
  } else {
    store.push(entry);
  }
  writeStore(store);
}

export function removeFromStore(name: string): boolean {
  const store = readStore();
  const index = store.findIndex((p) => p.name === name);
  if (index === -1) return false;
  store.splice(index, 1);
  writeStore(store);
  return true;
}

export function isInstalled(name: string): boolean {
  return readStore().some((p) => p.name === name);
}
