import { Command } from "commander";
import chalk from "chalk";
import { fetchPlugin } from "../lib/api-client";
import { addToStore, isInstalled } from "../lib/store";

/** Parse "plugin-name@channel" → { name, channel } */
function parsePluginArg(arg: string): { name: string; channel?: string } {
  const atIndex = arg.lastIndexOf("@");
  if (atIndex > 0) {
    return { name: arg.slice(0, atIndex), channel: arg.slice(atIndex + 1) };
  }
  return { name: arg };
}

export function installCommand(): Command {
  return new Command("install")
    .argument("<plugin>", "Plugin name to install, optionally with channel: <name>@<channel>")
    .description("Install a plugin from the registry")
    .action(async (pluginArg: string) => {
      const { name: pluginName, channel } = parsePluginArg(pluginArg);

      if (isInstalled(pluginName)) {
        console.log(chalk.yellow(`Plugin '${pluginName}' is already installed.`));
        return;
      }

      const channelLabel = channel ? chalk.gray(` (channel: ${channel})`) : "";
      console.log(chalk.cyan(`Looking up '${pluginName}'${channelLabel}...`));

      try {
        const plugin = await fetchPlugin(pluginName, channel);
        addToStore({
          name: plugin.name,
          version: plugin.version,
          description: plugin.description,
          author: plugin.author,
          channel: plugin.channel,
        });
        console.log(chalk.green(`✔ Installed ${plugin.name}@${plugin.version}`));
        console.log(chalk.gray(`  channel: ${plugin.channel}`));
        console.log(chalk.gray(`  ${plugin.description}`));
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        console.error(chalk.red(`✖ Failed to install '${pluginName}': ${msg}`));
        process.exit(1);
      }
    });
}
