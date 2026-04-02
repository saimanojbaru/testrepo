import { Command } from "commander";
import chalk from "chalk";
import { fetchPlugins } from "../lib/api-client";

export function searchCommand(): Command {
  return new Command("search")
    .argument("[query]", "Search term (name, description, or tag)")
    .description("Search for plugins in the registry")
    .action(async (query?: string) => {
      try {
        const plugins = await fetchPlugins(query);

        if (plugins.length === 0) {
          console.log(chalk.yellow("No plugins found."));
          return;
        }

        console.log(chalk.bold(`\nFound ${plugins.length} plugin(s):\n`));
        for (const p of plugins) {
          const tags = p.tags.length > 0 ? chalk.gray(`[${p.tags.join(", ")}]`) : "";
          console.log(`  ${chalk.cyan(p.name)}@${chalk.white(p.version)} ${tags}`);
          console.log(`    ${p.description}`);
          console.log(`    ${chalk.gray(`by ${p.author} · ${p.downloads} installs`)}`);
          console.log();
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        console.error(chalk.red(`✖ Search failed: ${msg}`));
        process.exit(1);
      }
    });
}
