// uses a gui and a config.yml
package com.example.dailyplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DailyPlugin extends JavaPlugin implements Listener {

    private ItemStack dailyReward;
    private HashMap<UUID, Long> lastClaimed;
    private File dataFile;
    private FileConfiguration data;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        
        lastClaimed = new HashMap<>();
        if (data.contains("lastClaimed")) {
            for (String uuid : data.getConfigurationSection("lastClaimed").getKeys(false)) {
                lastClaimed.put(UUID.fromString(uuid), data.getLong("lastClaimed." + uuid));
            }
        }

        dailyReward = new ItemStack(Material.DIAMOND);
        getCommand("daily").setExecutor(this);
        getCommand("setdaily").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        scheduleDailyTask();
    }

    private void scheduleDailyTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isTimeToRun()) {
                    lastClaimed.clear();
                    saveData();
                    Bukkit.broadcastMessage("§aDaily rewards have been reset!");
                }
            }
        }.runTaskTimer(this, 0L, 1200L);
    }

    private void saveData() {
        for (UUID uuid : lastClaimed.keySet()) {
            data.set("lastClaimed." + uuid.toString(), lastClaimed.get(uuid));
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isTimeToRun() {
        LocalTime now = LocalTime.now();
        return now.getHour() == 0 && now.getMinute() == 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("daily")) {
            openDailyGUI(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setdaily")) {
            if (!player.isOp()) {
                player.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType() == Material.AIR) {
                player.sendMessage("§cYou must hold an item to set it as the daily reward!");
                return true;
            }

            dailyReward = handItem.clone();
            player.sendMessage("§aDaily reward has been set to " + handItem.getType().name());
            return true;
        }

        return false;
    }

    private void openDailyGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6Daily Reward");
        
        if (lastClaimed.containsKey(player.getUniqueId()) && 
            System.currentTimeMillis() - lastClaimed.get(player.getUniqueId()) < 86400000) {
            ItemStack claimed = new ItemStack(Material.BARRIER);
            ItemMeta meta = claimed.getItemMeta();
            meta.setDisplayName("§cAlready Claimed!");
            List<String> lore = new ArrayList<>();
            long timeLeft = 86400000 - (System.currentTimeMillis() - lastClaimed.get(player.getUniqueId()));
            long hoursLeft = timeLeft / 3600000;
            lore.add("§7You can claim again in: §e" + hoursLeft + " hours");
            meta.setLore(lore);
            claimed.setItemMeta(meta);
            gui.setItem(13, claimed);
        } else {
            gui.setItem(13, dailyReward);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6Daily Reward")) return;
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (event.getSlot() != 13) return;
        
        if (lastClaimed.containsKey(player.getUniqueId()) && 
            System.currentTimeMillis() - lastClaimed.get(player.getUniqueId()) < 86400000) {
            return;
        }

        player.getInventory().addItem(dailyReward.clone());
        lastClaimed.put(player.getUniqueId(), System.currentTimeMillis());
        saveData();
        player.sendMessage("§aYou have claimed your daily reward!");
        player.closeInventory();
    }

    @Override
    public void onDisable() {
        saveData();
        Bukkit.getLogger().info("DailyPlugin has been disabled.");
    }
}
