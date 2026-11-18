package com.lockedupmc.worldguardtranslations;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class WgtTabCompleter implements TabCompleter {
    private final WorldGuardTranslations plugin;
    public WgtTabCompleter(WorldGuardTranslations plugin) { this.plugin = plugin; }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "status", "help").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
