import { Command } from "commander";
import chalk from "chalk";
import fs from "fs";
import path from "path";
import os from "os";
import { readStore, removeFromStore } from "../lib/store";

export function uninstallCommand(): Command {
  return new Command("uninstall")
    .argument("<plugin>", "Plugin name to uninstall")
    .description("Uninstall a locally installed plugin and remove its Claude Code skill")
    .action((pluginName: string) => {
      const store = readStore();
      const entry = store.find((p) => p.name === pluginName);

      const removed = removeFromStore(pluginName);
      if (!removed) {
        console.log(chalk.yellow(`Plugin '${pluginName}' is not installed.`));
        return;
      }

      // Remove SKILL.md from ~/.claude/skills/<name>/
      const skillDir = entry?.skillPath
        ? path.dirname(entry.skillPath)
        : path.join(os.homedir(), ".claude", "skills", pluginName);

      if (fs.existsSync(skillDir)) {
        fs.rmSync(skillDir, { recursive: true, force: true });
        console.log(chalk.gray(`  skill removed from ${skillDir}`));
      }

      console.log(chalk.green(`✔ Uninstalled '${pluginName}'`));
    });
}
