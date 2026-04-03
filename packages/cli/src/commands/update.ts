import { Command } from "commander";
import chalk from "chalk";
import fs from "fs";
import path from "path";
import os from "os";
import { fetchPlugin, fetchPluginSkill } from "../lib/api-client";
import { readStore, addToStore } from "../lib/store";

function writeSkillFile(pluginName: string, skillContent: string): string {
  const skillDir = path.join(os.homedir(), ".claude", "skills", pluginName);
  fs.mkdirSync(skillDir, { recursive: true });
  const skillPath = path.join(skillDir, "SKILL.md");
  fs.writeFileSync(skillPath, skillContent, "utf-8");
  return skillPath;
}

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
          const remote = await fetchPlugin(local.name, local.channel);
          if (remote.version !== local.version) {
            // Re-fetch and re-write the SKILL.md for the updated version
            let skillPath: string | undefined = local.skillPath;
            try {
              const skillContent = await fetchPluginSkill(remote.name, remote.channel);
              skillPath = writeSkillFile(remote.name, skillContent);
            } catch {
              // Skill update is best-effort; keep existing skillPath
            }

            addToStore({
              name: remote.name,
              version: remote.version,
              description: remote.description,
              author: remote.author,
              channel: remote.channel,
              skillPath,
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
