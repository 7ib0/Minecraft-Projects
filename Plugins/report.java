// report plugin that lets players report players or messages a player send, these will be stored and can be sen by staff
package me.Tibo.ReportPlugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ReportPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, List<String>> playerChatHistory = new HashMap<>();
    private final Map<UUID, UUID> reportingPlayers = new HashMap<>();
    private final List<String> reports = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("report").setExecutor(this::onReportCommand);
        getCommand("viewreports").setExecutor(this::onViewReportsCommand);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerChatHistory.putIfAbsent(playerId, new ArrayList<>());
        List<String> messages = playerChatHistory.get(playerId);
        messages.add(event.getMessage());
        if (messages.size() > 10) {
            messages.remove(0); // Keep only the last 10 messages
        }
    }

    private boolean onReportCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player reporter = (Player) sender;
            if (args.length != 1) {
                reporter.sendMessage(ChatColor.RED + "Usage: /report <player>");
                return true;
            }
            Player reported = Bukkit.getPlayer(args[0]);
            if (reported == null) {
                reporter.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            openReportMenu(reporter, reported);
        }
        return true;
    }

    private boolean onViewReportsCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player staff = (Player) sender;
            openReportsMenu(staff);
        }
        return true;
    }

    private void openReportMenu(Player reporter, Player reported) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.RED + "Report " + reported.getName());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + reported.getName());
        head.setItemMeta(meta);

        gui.setItem(4, head);

        String[] reasons = {"Hacking", "Abuse", "Spam", "Griefing"};
        for (int i = 0; i < reasons.length; i++) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta itemMeta = item.getItemMeta();
            itemMeta.setDisplayName(ChatColor.YELLOW + reasons[i]);
            item.setItemMeta(itemMeta);
            gui.setItem(i, item);
        }

        reportingPlayers.put(reporter.getUniqueId(), reported.getUniqueId());
        reporter.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.contains("Report")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            UUID reportedId = reportingPlayers.get(player.getUniqueId());
            if (reportedId == null) return;
            Player reported = Bukkit.getPlayer(reportedId);
            if (reported == null) {
                player.sendMessage(ChatColor.RED + "Reported player is no longer online.");
                return;
            }

            reports.add(reported.getName() + " reported for " + clicked.getItemMeta().getDisplayName());
            player.sendMessage(ChatColor.GREEN + "Report submitted!");
        } else if (title.equals(ChatColor.RED + "View Reports")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                reports.remove(clicked.getItemMeta().getDisplayName());
                player.sendMessage(ChatColor.GREEN + "Report removed.");
                openReportsMenu(player);
            }
        }
    }

    private void openReportsMenu(Player staff) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.RED + "View Reports");
        for (int i = 0; i < reports.size(); i++) {
            ItemStack reportItem = new ItemStack(Material.PAPER);
            ItemMeta meta = reportItem.getItemMeta();
            meta.setDisplayName(reports.get(i));
            reportItem.setItemMeta(meta);
            gui.setItem(i, reportItem);
        }
        staff.openInventory(gui);
    }
}
