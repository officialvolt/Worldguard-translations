package com.lockedupmc.worldguardtranslations;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGuardTranslations extends JavaPlugin implements Listener {
    private static final String PREFIX = "[WorldGuardTranslations] ";

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private RegionContainer regionContainer;
    private boolean worldGuardDefaultMessageSuppressed;
    private final Map<String, String> previousGlobalDenyMessages = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("wgt")).setExecutor(new WgtCommand(this));
        Objects.requireNonNull(getCommand("wgt")).setTabCompleter(new WgtTabCompleter(this));

        if (getConfig().getBoolean("worldguard.suppress-default-deny-message", true)) {
            suppressWorldGuardDefaultMessage();
        }

        getLogger().info("WorldGuardTranslations enabled.");
    }

    @Override
    public void onDisable() {
        restoreWorldGuardDefaultMessage();
    }

    public void reloadPlugin() {
        reloadConfig();
        if (getConfig().getBoolean("worldguard.suppress-default-deny-message", true)) {
            suppressWorldGuardDefaultMessage();
        } else {
            restoreWorldGuardDefaultMessage();
        }
        cooldowns.clear();
    }

    private void suppressWorldGuardDefaultMessage() {
        if (worldGuardDefaultMessageSuppressed) return;
        try {
            for (var world : Bukkit.getWorlds()) {
                RegionManager manager = regionContainer.get(BukkitAdapter.adapt(world));
                if (manager == null) continue;
                ProtectedRegion global = manager.getRegion("__global__");
                if (global == null) continue;

                previousGlobalDenyMessages.put(world.getName(), global.getFlag(Flags.DENY_MESSAGE));
                global.setFlag(Flags.DENY_MESSAGE, "");
                manager.saveChanges();
            }
            worldGuardDefaultMessageSuppressed = true;
            getLogger().info("WorldGuard global deny messages have been suppressed. WorldGuardTranslations now handles denial messages.");
        } catch (Exception ex) {
            getLogger().warning("Could not suppress WorldGuard's default deny message: " + ex.getMessage());
        }
    }

    private void restoreWorldGuardDefaultMessage() {
        if (!worldGuardDefaultMessageSuppressed) return;
        try {
            for (var world : Bukkit.getWorlds()) {
                RegionManager manager = regionContainer.get(BukkitAdapter.adapt(world));
                if (manager == null) continue;
                ProtectedRegion global = manager.getRegion("__global__");
                if (global == null) continue;
                global.setFlag(Flags.DENY_MESSAGE, previousGlobalDenyMessages.get(world.getName()));
                manager.saveChanges();
            }
        } catch (Exception ex) {
            getLogger().warning("Could not restore WorldGuard's global deny message: " + ex.getMessage());
        } finally {
            previousGlobalDenyMessages.clear();
            worldGuardDefaultMessageSuppressed = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "block-break", event.getBlock().getLocation(), event.getBlock().getType(), null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "block-place", event.getBlock().getLocation(), event.getBlock().getType(), null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.useInteractedBlock() != org.bukkit.event.Event.Result.DENY && event.useItemInHand() != org.bukkit.event.Event.Result.DENY) return;
        if (event.getPlayer() == null) return;
        Block block = event.getClickedBlock();
        String category = block != null && isContainer(block.getType()) ? "container" : "interact";
        send(event.getPlayer(), category, block == null ? event.getPlayer().getLocation() : block.getLocation(),
                block == null ? Material.AIR : block.getType(), null, event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSpecialInteract(PlayerInteractEvent event) {
        if (event.useInteractedBlock() != org.bukkit.event.Event.Result.DENY
                && event.useItemInHand() != org.bukkit.event.Event.Result.DENY) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material blockType = block == null ? Material.AIR : block.getType();
        ItemStack item = event.getItem();
        Material itemType = item == null ? Material.AIR : item.getType();

        String category = null;

        if (blockType == Material.RESPAWN_ANCHOR) {
            category = "respawn-anchors";
        } else if (itemType == Material.FLINT_AND_STEEL || itemType == Material.FIRE_CHARGE) {
            category = "lighter";
        } else if (blockType == Material.ANVIL || blockType == Material.CHIPPED_ANVIL
                || blockType == Material.DAMAGED_ANVIL) {
            category = "use-anvil";
        } else if (blockType == Material.BIG_DRIPLEAF || blockType == Material.SMALL_DRIPLEAF) {
            category = "use-dripleaf";
        }

        if (category != null) {
            send(player, category, block == null ? player.getLocation() : block.getLocation(),
                    blockType, null, item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "entity-interact", event.getRightClicked().getLocation(), null, event.getRightClicked(), event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;

        String category;
        if (event.getEntity() instanceof Player) {
            category = "pvp";
        } else if (event.getEntity() instanceof Animals) {
            category = "damage-animals";
        } else {
            category = "entity-damage";
        }

        send(player, category, event.getEntity().getLocation(), null, event.getEntity(),
                player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onFallDamage(EntityDamageEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        send(player, "fall-damage", player.getLocation(), null, null,
                player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "sleep", event.getBed().getLocation(), event.getBed().getType(),
                null, event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "item-frame-rotation", event.getItemFrame().getLocation(),
                null, event.getItemFrame(), event.getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getAttacker() instanceof Player player)) return;
        send(player, "vehicle-destroy", event.getVehicle().getLocation(),
                null, event.getVehicle(), player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!event.isCancelled()) return;
        if (!(event.getEntered() instanceof Player player)) return;
        send(player, "ride", event.getVehicle().getLocation(),
                null, event.getVehicle(), player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (!event.isCancelled()) return;
        send(event.getPlayer(), "item-drop", event.getItemDrop().getLocation(), null, null, event.getItemDrop().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (!event.isCancelled() || !(event.getEntity() instanceof Player player)) return;
        send(player, "item-pickup", event.getItem().getLocation(), null, null, event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        Player player = event.getPlayer();
        boolean wasDenied = !entryAllowed(player, event.getFrom());
        boolean nowDenied = !entryAllowed(player, event.getTo());
        if (!wasDenied && nowDenied) send(player, "entry", event.getTo(), null, null, null);
        else if (wasDenied && !nowDenied) send(player, "exit", event.getTo(), null, null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();

        if (event.isCancelled()) {
            String cause = event.getCause().name();
            if ("ENDER_PEARL".equals(cause)) {
                send(player, "enderpearl", event.getTo(), null, null,
                        player.getInventory().getItemInMainHand());
                return;
            }
            if ("CONSUMABLE_EFFECT".equals(cause)) {
                send(player, "chorus-fruit-teleport", event.getTo(), null, null,
                        player.getInventory().getItemInMainHand());
                return;
            }
            return;
        }

        boolean wasDenied = !entryAllowed(player, event.getFrom());
        boolean nowDenied = !entryAllowed(player, event.getTo());
        if (!wasDenied && nowDenied) send(player, "entry", event.getTo(), null, null, null);
        else if (wasDenied && !nowDenied) send(player, "exit", event.getFrom(), null, null, null);
    }

    private boolean entryAllowed(Player player, Location location) {
        try {
            RegionManager manager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return true;
            var set = manager.getApplicableRegions(com.sk89q.worldedit.math.BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            var localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            return set.testState(localPlayer, Flags.ENTRY);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void send(Player player, String type, Location location, Material material, Entity entity, ItemStack item) {
        if (!getConfig().getBoolean("messages." + type + ".enabled", true)) return;
        if (onCooldown(player, type)) return;

        String mode = getConfig().getString("messages." + type + ".type", "CHAT").toUpperCase(Locale.ROOT);
        if (mode.equals("NONE")) return;

        // TITLE messages use their own title/subtitle fields and do not require
        // the normal `message:` field to be present. CHAT and ACTION_BAR still
        // require `message:` as before.
        String raw = getConfig().getString("messages." + type + ".message", "");
        if (!mode.equals("TITLE") && (raw == null || raw.isBlank())) return;

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%world%", location.getWorld() == null ? "" : location.getWorld().getName());
        placeholders.put("%x%", String.valueOf(location.getBlockX()));
        placeholders.put("%y%", String.valueOf(location.getBlockY()));
        placeholders.put("%z%", String.valueOf(location.getBlockZ()));
        placeholders.put("%block%", material == null ? "" : material.name().toLowerCase(Locale.ROOT));
        placeholders.put("%entity%", entity == null ? "" : entity.getType().name().toLowerCase(Locale.ROOT));
        placeholders.put("%item%", item == null ? "" : item.getType().name().toLowerCase(Locale.ROOT));
        raw = replace(raw, placeholders);

        Component message = miniMessage.deserialize(raw);
        switch (mode) {
            case "CHAT" -> player.sendMessage(message);
            case "ACTION_BAR" -> player.sendActionBar(message);
            case "TITLE" -> {
                Component title = miniMessage.deserialize(replace(getConfig().getString("messages." + type + ".title", raw), placeholders));
                Component subtitle = miniMessage.deserialize(replace(getConfig().getString("messages." + type + ".subtitle", ""), placeholders));
                int fadeIn = getConfig().getInt("messages." + type + ".fade-in", 5);
                int stay = getConfig().getInt("messages." + type + ".stay", 40);
                int fadeOut = getConfig().getInt("messages." + type + ".fade-out", 10);
                player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle,
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(fadeIn * 50L), java.time.Duration.ofMillis(stay * 50L), java.time.Duration.ofMillis(fadeOut * 50L))));
            }
            default -> player.sendMessage(message);
        }
    }

    private boolean onCooldown(Player player, String type) {
        long seconds = getConfig().getLong("messages." + type + ".cooldown-seconds", getConfig().getLong("cooldown-seconds", 2));
        if (seconds <= 0) return false;
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        Long last = playerCooldowns.get(type);
        if (last != null && now - last < seconds * 1000L) return true;
        playerCooldowns.put(type, now);
        return false;
    }

    private String replace(String text, Map<String, String> placeholders) {
        for (var entry : placeholders.entrySet()) text = text.replace(entry.getKey(), entry.getValue());
        return text;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private boolean isContainer(Material material) {
        return material.name().contains("CHEST") || material.name().contains("BARREL") || material.name().contains("SHULKER") ||
                material.name().contains("HOPPER") || material.name().contains("DISPENSER") || material.name().contains("DROPPER") ||
                material.name().contains("FURNACE") || material.name().contains("SMOKER") || material.name().contains("BLAST_FURNACE") ||
                material.name().contains("BREWING_STAND");
    }
}
