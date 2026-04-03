#!/usr/bin/env node
import { Command } from "commander";
import { installCommand } from "./commands/install";
import { uninstallCommand } from "./commands/uninstall";
import { searchCommand } from "./commands/search";
import { listCommand } from "./commands/list";
import { infoCommand } from "./commands/info";
import { updateCommand } from "./commands/update";

const program = new Command();

program
  .name("ccpi")
  .description("Claude Code Plugin Installer — browse, install, and manage Claude Code plugins")
  .version("1.0.0");

program.addCommand(installCommand());
program.addCommand(uninstallCommand());
program.addCommand(searchCommand());
program.addCommand(listCommand());
program.addCommand(infoCommand());
program.addCommand(updateCommand());

program.parse(process.argv);
