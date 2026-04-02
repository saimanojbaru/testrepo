import { Request, Response } from "express";
import fs from "fs";
import path from "path";

const DATA_FILE = path.join(__dirname, "../data/plugins.json");

interface Plugin {
  name: string;
  version: string;
  description: string;
  author: string;
  tags: string[];
  downloads: number;
  createdAt: string;
}

function readPlugins(): Plugin[] {
  const raw = fs.readFileSync(DATA_FILE, "utf-8");
  return JSON.parse(raw) as Plugin[];
}

function writePlugins(plugins: Plugin[]): void {
  fs.writeFileSync(DATA_FILE, JSON.stringify(plugins, null, 2), "utf-8");
}

export function listPlugins(req: Request, res: Response): void {
  const plugins = readPlugins();
  const q = req.query.q as string | undefined;
  if (q) {
    const query = q.toLowerCase();
    const filtered = plugins.filter(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        p.description.toLowerCase().includes(query) ||
        p.tags.some((t) => t.toLowerCase().includes(query))
    );
    res.json(filtered);
    return;
  }
  res.json(plugins);
}

export function getPlugin(req: Request, res: Response): void {
  const plugins = readPlugins();
  const plugin = plugins.find((p) => p.name === req.params.name);
  if (!plugin) {
    res.status(404).json({ error: `Plugin '${req.params.name}' not found` });
    return;
  }
  res.json(plugin);
}

export function publishPlugin(req: Request, res: Response): void {
  const plugins = readPlugins();
  const body = req.body as Partial<Plugin>;

  if (!body.name || !body.version || !body.description || !body.author) {
    res.status(400).json({ error: "name, version, description, and author are required" });
    return;
  }

  if (plugins.find((p) => p.name === body.name)) {
    res.status(409).json({ error: `Plugin '${body.name}' already exists` });
    return;
  }

  const newPlugin: Plugin = {
    name: body.name,
    version: body.version,
    description: body.description,
    author: body.author,
    tags: body.tags ?? [],
    downloads: 0,
    createdAt: new Date().toISOString(),
  };

  plugins.push(newPlugin);
  writePlugins(plugins);
  res.status(201).json(newPlugin);
}

export function deletePlugin(req: Request, res: Response): void {
  const plugins = readPlugins();
  const index = plugins.findIndex((p) => p.name === req.params.name);
  if (index === -1) {
    res.status(404).json({ error: `Plugin '${req.params.name}' not found` });
    return;
  }
  const [removed] = plugins.splice(index, 1);
  writePlugins(plugins);
  res.json({ message: `Plugin '${removed.name}' removed` });
}
