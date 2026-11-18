package com.lockedupmc.worldguardtranslations;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class WgtCommand implements CommandExecutor {
    private final WorldGuardTranslations plugin;
    public WgtCommand(WorldGuardTranslations plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("worldguardtranslations.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "WorldGuardTranslations commands:");
            sender.sendMessage(ChatColor.GRAY + "/wgt reload" + ChatColor.WHITE + " - Reload configuration");
            sender.sendMessage(ChatColor.GRAY + "/wgt status" + ChatColor.WHITE + " - Show plugin status");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(ChatColor.GREEN + "WorldGuardTranslations configuration reloaded.");
            }
            case "status" -> {
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "WorldGuardTranslations " + ChatColor.GRAY + "v" + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.GRAY + "WorldGuard default deny message suppression: " + (plugin.getConfig().getBoolean("worldguard.suppress-default-deny-message", true) ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /wgt help");
        }
        return true;
    }
}
