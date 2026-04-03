import { Command } from "commander";
import chalk from "chalk";
import fs from "fs";
import path from "path";
import os from "os";
import { fetchPlugin, fetchPluginSkill } from "../lib/api-client";
import { addToStore, isInstalled } from "../lib/store";

/** Parse "plugin-name@channel" → { name, channel } */
function parsePluginArg(arg: string): { name: string; channel?: string } {
  const atIndex = arg.lastIndexOf("@");
  if (atIndex > 0) {
    return { name: arg.slice(0, atIndex), channel: arg.slice(atIndex + 1) };
  }
  return { name: arg };
}

function writeSkillFile(pluginName: string, skillContent: string): string {
  const skillDir = path.join(os.homedir(), ".claude", "skills", pluginName);
  fs.mkdirSync(skillDir, { recursive: true });
  const skillPath = path.join(skillDir, "SKILL.md");
  fs.writeFileSync(skillPath, skillContent, "utf-8");
  return skillPath;
}

export function installCommand(): Command {
  return new Command("install")
    .argument("<plugin>", "Plugin name to install, optionally with channel: <name>@<channel>")
    .description("Install a plugin from the registry and register it as a Claude Code skill")
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

        // Fetch and write SKILL.md to ~/.claude/skills/<name>/SKILL.md
        let skillPath: string | undefined;
        try {
          const skillContent = await fetchPluginSkill(plugin.name, channel);
          skillPath = writeSkillFile(plugin.name, skillContent);
        } catch {
          // Skill content is optional — log a warning but don't abort install
          console.log(chalk.yellow(`  warning: no skill found for '${plugin.name}', installing metadata only`));
        }

        addToStore({
          name: plugin.name,
          version: plugin.version,
          description: plugin.description,
          author: plugin.author,
          channel: plugin.channel,
          skillPath,
        });

        console.log(chalk.green(`✔ Installed ${plugin.name}@${plugin.version}`));
        console.log(chalk.gray(`  channel: ${plugin.channel}`));
        console.log(chalk.gray(`  ${plugin.description}`));
        if (skillPath) {
          console.log(chalk.gray(`  skill registered → ${skillPath}`));
          console.log(chalk.gray(`  restart Claude Code to activate trigger phrases`));
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        console.error(chalk.red(`✖ Failed to install '${pluginName}': ${msg}`));
        process.exit(1);
      }
    });
}
