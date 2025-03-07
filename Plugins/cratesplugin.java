// plugin that lets you turn a chest into a crate, you can also change the prizes
package me.tibo.cratesplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

public class CratesPlugin extends JavaPlugin implements Listener {

    private Map<Location, List<ItemStack>> crateLocations = new HashMap<>();
    private Map<UUID, Long> cooldowns = new HashMap<>();
    private final long COOLDOWN_TIME = 300000; 

    private FileConfiguration config;

    @Override
    public void onEnable() {
        getLogger().info("CratesPlugin enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("createcrate").setExecutor(this);
        
        saveDefaultConfig();
        config = getConfig();
        loadCrates();
    }

    @Override
    public void onDisable() {
        saveCrates();
        getLogger().info("CratesPlugin disabled!");
    }

    private void loadCrates() {
        if (config.contains("crates")) {
            for (String locationStr : config.getConfigurationSection("crates").getKeys(false)) {
                String[] coords = locationStr.split(",");
                Location loc = new Location(getServer().getWorld(coords[0]), 
                    Double.parseDouble(coords[1]), 
                    Double.parseDouble(coords[2]), 
                    Double.parseDouble(coords[3]));
                
                List<ItemStack> rewards = (List<ItemStack>) config.getList("crates." + locationStr);
                crateLocations.put(loc, rewards);
            }
        }
    }

    private void saveCrates() {
        for (Map.Entry<Location, List<ItemStack>> entry : crateLocations.entrySet()) {
            Location loc = entry.getKey();
            String locationStr = loc.getWorld().getName() + "," + 
                               loc.getX() + "," + 
                               loc.getY() + "," + 
                               loc.getZ();
            config.set("crates." + locationStr, entry.getValue());
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("createcrate")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You need to be an operator to create crates!");
                return true;
            }

            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /createcrate <reward1> <reward2> ...");
                return true;
            }

            Block targetBlock = player.getTargetBlock(null, 5);
            if (targetBlock.getType() != Material.CHEST) {
                player.sendMessage(ChatColor.RED + "You must look at a chest to create a crate!");
                return true;
            }

            List<ItemStack> rewards = new ArrayList<>();
            for (String reward : args) {
                Material material = Material.matchMaterial(reward.toUpperCase());
                if (material != null) {
                    rewards.add(createReward(material, 1, "§6" + material.name() + " Reward"));
                }
            }

            crateLocations.put(targetBlock.getLocation(), rewards);
            player.sendMessage(ChatColor.GREEN + "Crate created successfully with " + rewards.size() + " possible rewards!");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.CHEST) return;
        
        if (crateLocations.containsKey(clickedBlock.getLocation())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            
            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = (cooldowns.get(player.getUniqueId()) + COOLDOWN_TIME - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    player.sendMessage(ChatColor.RED + "You must wait " + timeLeft + " seconds before opening another crate!");
                    return;
                }
            }
            
            openCrate(player, clickedBlock.getLocation());
        }
    }

    private void openCrate(Player player, Location crateLocation) {
        List<ItemStack> rewards = crateLocations.get(crateLocation);
        if (rewards == null || rewards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This crate has no rewards configured!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Opening crate...");
        
        new BukkitRunnable() {
            int ticks = 0;
            Random random = new Random();
            
            @Override
            public void run() {
                if (ticks >= 20) {
                    ItemStack reward = rewards.get(random.nextInt(rewards.size()));
                    player.getInventory().addItem(reward);
                    player.sendMessage(ChatColor.GREEN + "You won: " + reward.getItemMeta().getDisplayName());
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    cancel();
                    return;
                }
                
                ItemStack displayItem = rewards.get(random.nextInt(rewards.size()));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                player.sendTitle("", ChatColor.GOLD + "Rolling...", 0, 10, 0);
                
                ticks++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private ItemStack createReward(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = Arrays.asList("§7Crate Reward", "§7Right-click to use");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
