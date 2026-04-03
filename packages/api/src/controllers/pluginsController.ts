import { Request, Response } from "express";
import fs from "fs";
import path from "path";

const DATA_FILE = path.join(__dirname, "../data/plugins.json");

interface Plugin {
  name: string;
  version: string;
  description: string;
  author: string;
  channel: string;
  tags: string[];
  downloads: number;
  createdAt: string;
  skillContent?: string;
}

function readPlugins(): Plugin[] {
  const raw = fs.readFileSync(DATA_FILE, "utf-8");
  return JSON.parse(raw) as Plugin[];
}

function writePlugins(plugins: Plugin[]): void {
  fs.writeFileSync(DATA_FILE, JSON.stringify(plugins, null, 2), "utf-8");
}

function findPlugin(plugins: Plugin[], name: string, channel?: string): Plugin | undefined {
  let plugin = plugins.find((p) => p.name === name && (!channel || p.channel === channel));
  // Fall back to any channel if not found in specified channel
  if (!plugin && channel) plugin = plugins.find((p) => p.name === name);
  return plugin;
}

export function listPlugins(req: Request, res: Response): void {
  let plugins = readPlugins();

  const q = req.query.q as string | undefined;
  const channel = req.query.channel as string | undefined;

  if (channel) {
    plugins = plugins.filter((p) => p.channel === channel);
  }

  if (q) {
    const query = q.toLowerCase();
    plugins = plugins.filter(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        p.description.toLowerCase().includes(query) ||
        p.tags.some((t) => t.toLowerCase().includes(query))
    );
  }

  // Omit skillContent from list responses to keep payloads small
  res.json(plugins.map(({ skillContent: _sc, ...rest }) => rest));
}

export function getPlugin(req: Request, res: Response): void {
  const plugins = readPlugins();
  const channel = req.query.channel as string | undefined;
  const plugin = findPlugin(plugins, req.params.name, channel);

  if (!plugin) {
    res.status(404).json({ error: `Plugin '${req.params.name}' not found` });
    return;
  }
  const { skillContent: _sc, ...rest } = plugin;
  res.json(rest);
}

export function getPluginSkill(req: Request, res: Response): void {
  const plugins = readPlugins();
  const channel = req.query.channel as string | undefined;
  const plugin = findPlugin(plugins, req.params.name, channel);

  if (!plugin || !plugin.skillContent) {
    res.status(404).json({ error: `Skill for '${req.params.name}' not found` });
    return;
  }
  res.type("text/plain").send(plugin.skillContent);
}

export function publishPlugin(req: Request, res: Response): void {
  const plugins = readPlugins();
  const body = req.body as Partial<Plugin>;

  if (!body.name || !body.version || !body.description || !body.author) {
    res.status(400).json({ error: "name, version, description, and author are required" });
    return;
  }

  const targetChannel = body.channel ?? "community";
  if (plugins.find((p) => p.name === body.name && p.channel === targetChannel)) {
    res.status(409).json({ error: `Plugin '${body.name}' already exists in channel '${targetChannel}'` });
    return;
  }

  const newPlugin: Plugin = {
    name: body.name,
    version: body.version,
    description: body.description,
    author: body.author,
    channel: targetChannel,
    tags: body.tags ?? [],
    downloads: 0,
    createdAt: new Date().toISOString(),
    skillContent: body.skillContent,
  };

  plugins.push(newPlugin);
  writePlugins(plugins);
  const { skillContent: _sc, ...rest } = newPlugin;
  res.status(201).json(rest);
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
