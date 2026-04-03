import axios from "axios";

const BASE_URL = process.env.CCPI_REGISTRY_URL ?? "http://localhost:3000";

export interface Plugin {
  name: string;
  version: string;
  description: string;
  author: string;
  channel: string;
  tags: string[];
  downloads: number;
  createdAt: string;
}

const client = axios.create({ baseURL: BASE_URL });

export async function fetchPlugins(query?: string, channel?: string): Promise<Plugin[]> {
  const params: Record<string, string> = {};
  if (query) params.q = query;
  if (channel) params.channel = channel;
  const { data } = await client.get<Plugin[]>("/plugins", { params });
  return data;
}

export async function fetchPlugin(name: string, channel?: string): Promise<Plugin> {
  const params = channel ? { channel } : undefined;
  const { data } = await client.get<Plugin>(`/plugins/${name}`, { params });
  return data;
}

export async function publishPlugin(plugin: Omit<Plugin, "downloads" | "createdAt">): Promise<Plugin> {
  const { data } = await client.post<Plugin>("/plugins", plugin);
  return data;
}

export async function removePlugin(name: string): Promise<{ message: string }> {
  const { data } = await client.delete<{ message: string }>(`/plugins/${name}`);
  return data;
}
