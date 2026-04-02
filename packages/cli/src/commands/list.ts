import { Command } from "commander";
import chalk from "chalk";
import { readStore } from "../lib/store";

export function listCommand(): Command {
  return new Command("list")
    .description("List all locally installed plugins")
    .action(() => {
      const installed = readStore();

      if (installed.length === 0) {
        console.log(chalk.yellow("No plugins installed. Run `ccpi search` to browse available plugins."));
        return;
      }

      console.log(chalk.bold(`\nInstalled plugins (${installed.length}):\n`));
      for (const p of installed) {
        const date = new Date(p.installedAt).toLocaleDateString();
        console.log(`  ${chalk.cyan(p.name)}@${chalk.white(p.version)}`);
        console.log(`    ${p.description}`);
        console.log(`    ${chalk.gray(`by ${p.author} · installed ${date}`)}`);
        console.log();
      }
    });
}
