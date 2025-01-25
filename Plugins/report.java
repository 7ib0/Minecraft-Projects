package me.Tibo.report;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class Report extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Register the "report" command
        getCommand("report").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }

                if (args.length != 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /report <player>");
                    return true;
                }

                Player reportedPlayer = Bukkit.getPlayer(args[0]);
                if (reportedPlayer == null || !reportedPlayer.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return true;
                }

                openReportGUI((Player) sender, reportedPlayer.getName());
                return true;
            }
        });
    }

    private void openReportGUI(Player player, String reportedPlayer) {
        // Create a 9-slot inventory for reporting
        Inventory gui = Bukkit.createInventory(player, 9, "Report " + reportedPlayer);

        // Add report categories as enchanted books
        String[] categories = {"Cheating", "Spamming", "Discrimination", "Boosting"};
        for (int i = 0; i < categories.length; i++) {
            gui.setItem(i, createEnchantedBook(categories[i]));
        }

        player.openInventory(gui);
    }

    private ItemStack createEnchantedBook(String category) {
        // Create an enchanted book item
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        if (meta != null) {
            // Set the display name of the book to the category name in white
            meta.setDisplayName(ChatColor.WHITE + category);

            // Add a simple enchantment to give the book a glowing effect
            meta.addEnchant(Enchantment.LURE, 1, false);

            // Hide the enchantment details to keep the item clean
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Optionally, add lore to describe the reason
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Report reason:", ChatColor.WHITE + category));

            book.setItemMeta(meta);
        }

        return book;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory title starts with "Report "
        if (event.getView().getTitle().startsWith("Report ")) {
            event.setCancelled(true); // Prevent inventory interaction

            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
                return;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String category = ChatColor.stripColor(meta.getDisplayName());
                Player reporter = (Player) event.getWhoClicked();
                String reportedPlayer = event.getView().getTitle().replace("Report ", "");

                // Notify the reporter and log the report
                reporter.sendMessage(ChatColor.GREEN + "Thank you for reporting " + reportedPlayer + " for " + category + ". Our staff will review this report shortly.");
                Bukkit.getLogger().info(reporter.getName() + " reported " + reportedPlayer + " for " + category);

                reporter.closeInventory();
            }
        }
    }
}
