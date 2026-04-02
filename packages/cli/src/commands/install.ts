import { Command } from "commander";
import chalk from "chalk";
import { fetchPlugin } from "../lib/api-client";
import { addToStore, isInstalled } from "../lib/store";

export function installCommand(): Command {
  return new Command("install")
    .argument("<plugin>", "Plugin name to install")
    .description("Install a plugin from the registry")
    .action(async (pluginName: string) => {
      if (isInstalled(pluginName)) {
        console.log(chalk.yellow(`Plugin '${pluginName}' is already installed.`));
        return;
      }

      console.log(chalk.cyan(`Looking up '${pluginName}'...`));

      try {
        const plugin = await fetchPlugin(pluginName);
        addToStore({
          name: plugin.name,
          version: plugin.version,
          description: plugin.description,
          author: plugin.author,
        });
        console.log(chalk.green(`✔ Installed ${plugin.name}@${plugin.version}`));
        console.log(chalk.gray(`  ${plugin.description}`));
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        console.error(chalk.red(`✖ Failed to install '${pluginName}': ${msg}`));
        process.exit(1);
      }
    });
}
