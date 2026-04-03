import { Command } from "commander";
import chalk from "chalk";
import { fetchPlugin } from "../lib/api-client";
import { readStore, addToStore } from "../lib/store";

export function updateCommand(): Command {
  return new Command("update")
    .description("Update all installed plugins to their latest registry versions")
    .action(async () => {
      const installed = readStore();

      if (installed.length === 0) {
        console.log(chalk.yellow("No plugins installed. Run `ccpi search` to browse available plugins."));
        return;
      }

      console.log(chalk.bold(`\nChecking ${installed.length} plugin(s) for updates...\n`));

      let updatedCount = 0;

      for (const local of installed) {
        try {
          const remote = await fetchPlugin(local.name);
          if (remote.version !== local.version) {
            addToStore({
              name: remote.name,
              version: remote.version,
              description: remote.description,
              author: remote.author,
            });
            console.log(
              `  ${chalk.cyan(local.name)}: ${chalk.gray(local.version)} → ${chalk.green(remote.version)}`
            );
            updatedCount++;
          } else {
            console.log(`  ${chalk.cyan(local.name)}: ${chalk.gray(`already up to date (${local.version})`)}`);
          }
        } catch {
          console.log(`  ${chalk.cyan(local.name)}: ${chalk.red("failed to fetch from registry")}`);
        }
      }

      console.log();
      if (updatedCount > 0) {
        console.log(chalk.green(`✔ Updated ${updatedCount} plugin(s).`));
      } else {
        console.log(chalk.gray("All plugins are already up to date."));
      }
    });
}
