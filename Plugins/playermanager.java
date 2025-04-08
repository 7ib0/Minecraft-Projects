// plugin that lets you manage a plyer, opens a gui where you can ban, mute, kick, freeze or tp to the player
package me.tibo.playermanager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class PlayerManager extends JavaPlugin implements Listener {
    private List<UUID> frozenPlayers;
    private List<UUID> mutedPlayers;
    private FileConfiguration config;
    private File configFile;

    @Override
    public void onEnable() {
        frozenPlayers = new ArrayList<>();
        mutedPlayers = new ArrayList<>();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("playermanager").setExecutor(this);
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getString("messages.console-error", "§cThis command can only be used by players!"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission(config.getString("permissions.use", "playermanager.use"))) {
            player.sendMessage(config.getString("messages.no-permission", "§cYou don't have permission to use this command!"));
            return true;
        }

        openPlayerManagerGUI(player);
        return true;
    }

    private void openPlayerManagerGUI(Player manager) {
        Inventory gui = Bukkit.createInventory(null, 54, config.getString("gui.main-title", "§6Player Manager"));

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(manager)) {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
                meta.setOwningPlayer(target);
                meta.setDisplayName(config.getString("gui.player-name", "§e%player%").replace("%player%", target.getName()));

                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("gui.player-lore")) {
                    lore.add(line.replace("%status%", getPlayerStatus(target)));
                }
                meta.setLore(lore);

                playerHead.setItemMeta(meta);
                gui.setItem(slot++, playerHead);
            }
        }

        manager.openInventory(gui);
    }

    private String getPlayerStatus(Player player) {
        List<String> status = new ArrayList<>();
        if (frozenPlayers.contains(player.getUniqueId())) status.add(config.getString("status.frozen", "§cFrozen"));
        if (mutedPlayers.contains(player.getUniqueId())) status.add(config.getString("status.muted", "§cMuted"));
        return status.isEmpty() ? config.getString("status.normal", "§aNormal") : String.join(", ", status);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(config.getString("gui.main-title", "§6Player Manager"))) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player manager = (Player) event.getWhoClicked();
        String targetName = event.getCurrentItem().getItemMeta().getDisplayName().replace("§e", "");
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            manager.sendMessage(config.getString("messages.player-offline", "§cPlayer is no longer online!"));
            return;
        }

        openPlayerActionsGUI(manager, target);
    }

    private void openPlayerActionsGUI(Player manager, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, config.getString("gui.manage-title", "§6Manage %player%").replace("%player%", target.getName()));

        ItemStack kickItem = createGuiItem(Material.BARRIER, config.getString("gui.kick-title", "§cKick Player"),
                config.getString("gui.kick-lore", "§7Click to kick %player%").replace("%player%", target.getName()));

        ItemStack banItem = createGuiItem(Material.ANVIL, config.getString("gui.ban-title", "§cBan Player"),
                config.getString("gui.ban-lore", "§7Click to ban %player%").replace("%player%", target.getName()));

        ItemStack muteItem = createGuiItem(Material.PAPER, config.getString("gui.mute-title", "§cMute Player"),
                config.getString("gui.mute-lore", "§7Click to mute/unmute %player%").replace("%player%", target.getName()));

        ItemStack freezeItem = createGuiItem(Material.ICE, config.getString("gui.freeze-title", "§cFreeze Player"),
                config.getString("gui.freeze-lore", "§7Click to freeze/unfreeze %player%").replace("%player%", target.getName()));

        ItemStack tpItem = createGuiItem(Material.ENDER_PEARL, config.getString("gui.teleport-title", "§cTeleport to Player"),
                config.getString("gui.teleport-lore", "§7Click to teleport to %player%").replace("%player%", target.getName()));

        gui.setItem(10, kickItem);
        gui.setItem(11, banItem);
        gui.setItem(12, muteItem);
        gui.setItem(14, freezeItem);
        gui.setItem(16, tpItem);

        manager.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerActionClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(config.getString("gui.manage-title", "§6Manage "))) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player manager = (Player) event.getWhoClicked();
        String targetName = event.getView().getTitle().replace(config.getString("gui.manage-title", "§6Manage "), "");
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            manager.sendMessage(config.getString("messages.player-offline", "§cPlayer is no longer online!"));
            return;
        }

        switch (event.getCurrentItem().getType()) {
            case BARRIER:
                target.kickPlayer(config.getString("messages.kick", "§cYou have been kicked by %player%").replace("%player%", manager.getName()));
                manager.sendMessage(config.getString("messages.kick-success", "§aYou have kicked %player%").replace("%player%", target.getName()));
                break;
            case ANVIL:
                target.kickPlayer(config.getString("messages.ban", "§cYou have been banned by %player%").replace("%player%", manager.getName()));
                manager.sendMessage(config.getString("messages.ban-success", "§aYou have banned %player%").replace("%player%", target.getName()));
                break;
            case PAPER:
                toggleMute(target);
                manager.sendMessage(config.getString("messages.mute-toggle", "§aYou have %action% %player%")
                        .replace("%action%", mutedPlayers.contains(target.getUniqueId()) ? "muted" : "unmuted")
                        .replace("%player%", target.getName()));
                break;
            case ICE:
                toggleFreeze(target);
                manager.sendMessage(config.getString("messages.freeze-toggle", "§aYou have %action% %player%")
                        .replace("%action%", frozenPlayers.contains(target.getUniqueId()) ? "frozen" : "unfrozen")
                        .replace("%player%", target.getName()));
                break;
            case ENDER_PEARL:
                manager.teleport(target.getLocation());
                manager.sendMessage(config.getString("messages.teleport", "§aYou have teleported to %player%").replace("%player%", target.getName()));
                break;
        }
    }

    private void toggleMute(Player player) {
        UUID uuid = player.getUniqueId();
        if (mutedPlayers.contains(uuid)) {
            mutedPlayers.remove(uuid);
        } else {
            mutedPlayers.add(uuid);
        }
    }

    private void toggleFreeze(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.contains(uuid)) {
            frozenPlayers.remove(uuid);
        } else {
            frozenPlayers.add(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (mutedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getString("messages.muted", "§cYou are muted and cannot chat!"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        
    }
}
