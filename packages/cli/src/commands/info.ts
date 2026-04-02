import { Command } from "commander";
import chalk from "chalk";
import { fetchPlugin } from "../lib/api-client";
import { isInstalled } from "../lib/store";

export function infoCommand(): Command {
  return new Command("info")
    .argument("<plugin>", "Plugin name")
    .description("Show details about a plugin from the registry")
    .action(async (pluginName: string) => {
      try {
        const plugin = await fetchPlugin(pluginName);
        const installed = isInstalled(pluginName);

        console.log();
        console.log(`${chalk.bold(plugin.name)}@${chalk.white(plugin.version)} ${installed ? chalk.green("(installed)") : ""}`);
        console.log();
        console.log(`  ${chalk.bold("Description:")} ${plugin.description}`);
        console.log(`  ${chalk.bold("Author:")}      ${plugin.author}`);
        console.log(`  ${chalk.bold("Tags:")}        ${plugin.tags.join(", ") || "none"}`);
        console.log(`  ${chalk.bold("Downloads:")}   ${plugin.downloads.toLocaleString()}`);
        console.log(`  ${chalk.bold("Published:")}   ${new Date(plugin.createdAt).toLocaleDateString()}`);
        console.log();

        if (!installed) {
          console.log(chalk.gray(`  Run ${chalk.cyan(`ccpi install ${plugin.name}`)} to install.`));
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        console.error(chalk.red(`✖ Could not fetch plugin info: ${msg}`));
        process.exit(1);
      }
    });
}
