import { Command } from "commander";
import chalk from "chalk";
import { removeFromStore } from "../lib/store";

export function uninstallCommand(): Command {
  return new Command("uninstall")
    .argument("<plugin>", "Plugin name to uninstall")
    .description("Uninstall a locally installed plugin")
    .action((pluginName: string) => {
      const removed = removeFromStore(pluginName);
      if (removed) {
        console.log(chalk.green(`✔ Uninstalled '${pluginName}'`));
      } else {
        console.log(chalk.yellow(`Plugin '${pluginName}' is not installed.`));
      }
    });
}
