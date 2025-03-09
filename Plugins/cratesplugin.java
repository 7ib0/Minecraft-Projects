import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class CratesPlugin extends JavaPlugin implements Listener {

    private final Map<String, FileConfiguration> crateConfigs = new HashMap<>();
    private final Map<String, Location> crateLocations = new HashMap<>();
    private final Map<UUID, String> playerEditing = new HashMap<>();
    private final Set<UUID> confirmingDelete = new HashSet<>();
    private File cratesFolder;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cratesFolder = new File(getDataFolder(), "crates");
        if (!cratesFolder.exists()) {
            cratesFolder.mkdirs();
        }

        loadAllCrates();
        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("CratesPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        saveAllCrates();
        getLogger().info("CratesPlugin disabled successfully!");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("crate")).setExecutor(new CrateCommand());
    }

    private void loadAllCrates() {
        File[] files = cratesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String crateName = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            crateConfigs.put(crateName, config);
            
            if (config.contains("location")) {
                Location loc = Location.deserialize(config.getConfigurationSection("location").getValues(false));
                crateLocations.put(crateName, loc);
            }
        }
    }

    private void saveAllCrates() {
        for (Map.Entry<String, FileConfiguration> entry : crateConfigs.entrySet()) {
            try {
                entry.getValue().save(new File(cratesFolder, entry.getKey() + ".yml"));
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save crate: " + entry.getKey(), e);
            }
        }
    }

    private class CrateCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("crates.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length < 1) {
                sendHelpMessage(player);
                return true;
            }

            String action = args[0].toLowerCase();
            String crateName = args.length > 1 ? args[1].toLowerCase() : null;

            switch (action) {
                case "create":
                    if (crateName == null) {
                        player.sendMessage(ChatColor.RED + "Usage: /crate create <name>");
                        return true;
                    }
                    handleCreate(player, crateName);
                    break;
                    
                case "edit":
                    if (crateName == null) {
                        player.sendMessage(ChatColor.RED + "Usage: /crate edit <name>");
                        return true;
                    }
                    handleEdit(player, crateName);
                    break;
                    
                case "delete":
                    if (crateName == null) {
                        player.sendMessage(ChatColor.RED + "Usage: /crate delete <name>");
                        return true;
                    }
                    handleDelete(player, crateName);
                    break;
                    
                case "list":
                    handleList(player);
                    break;
                    
                default:
                    sendHelpMessage(player);
                    break;
            }

            return true;
        }

        private void sendHelpMessage(Player player) {
            player.sendMessage(ChatColor.GOLD + "=== Crates Help ===");
            player.sendMessage(ChatColor.YELLOW + "/crate create <name> " + ChatColor.GRAY + "- Create a new crate");
            player.sendMessage(ChatColor.YELLOW + "/crate edit <name> " + ChatColor.GRAY + "- Edit an existing crate");
            player.sendMessage(ChatColor.YELLOW + "/crate delete <name> " + ChatColor.GRAY + "- Delete a crate");
            player.sendMessage(ChatColor.YELLOW + "/crate list " + ChatColor.GRAY + "- List all crates");
        }

        private void handleCreate(Player player, String crateName) {
            if (crateExists(crateName)) {
                player.sendMessage(ChatColor.RED + "A crate with that name already exists!");
                return;
            }
            openCrateCreationGUI(player, crateName);
            playerEditing.put(player.getUniqueId(), crateName);
        }

        private void handleEdit(Player player, String crateName) {
            if (!crateExists(crateName)) {
                player.sendMessage(ChatColor.RED + "That crate doesn't exist!");
                return;
            }
            openCrateEditGUI(player, crateName);
            playerEditing.put(player.getUniqueId(), crateName);
        }

        private void handleDelete(Player player, String crateName) {
            if (!crateExists(crateName)) {
                player.sendMessage(ChatColor.RED + "That crate doesn't exist!");
                return;
            }

            if (!confirmingDelete.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Are you sure you want to delete this crate?");
                player.sendMessage(ChatColor.RED + "Type the command again within 10 seconds to confirm.");
                confirmingDelete.add(player.getUniqueId());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        confirmingDelete.remove(player.getUniqueId());
                    }
                }.runTaskLater(CratesPlugin.this, 200L);
                return;
            }

            confirmingDelete.remove(player.getUniqueId());
            crateConfigs.remove(crateName);
            crateLocations.remove(crateName);
            new File(cratesFolder, crateName + ".yml").delete();
            player.sendMessage(ChatColor.GREEN + "Crate deleted successfully!");
        }

        private void handleList(Player player) {
            if (crateConfigs.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No crates have been created yet.");
                return;
            }

            player.sendMessage(ChatColor.GOLD + "=== Available Crates ===");
            for (String crateName : crateConfigs.keySet()) {
                Location loc = crateLocations.get(crateName);
                String locationInfo = loc != null ? 
                    String.format(" at (%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ()) :
                    " (no location set)";
                player.sendMessage(ChatColor.YELLOW + "- " + crateName + ChatColor.GRAY + locationInfo);
            }
        }
    }

    private boolean crateExists(String name) {
        return crateConfigs.containsKey(name.toLowerCase());
    }

    private void openCrateCreationGUI(Player player, String crateName) {
        Inventory gui = Bukkit.createInventory(null, 36, "Create Crate: " + crateName);

        ItemStack locationItem = createGuiItem(Material.COMPASS, ChatColor.GREEN + "Set Location", 
            ChatColor.GRAY + "Click to set the crate location",
            ChatColor.GRAY + "to your current position");
        
        ItemStack prizesItem = createGuiItem(Material.CHEST, ChatColor.GREEN + "Set Prizes",
            ChatColor.GRAY + "Click to configure the prizes",
            ChatColor.GRAY + "that can be won from this crate");
        
        ItemStack keyItem = createGuiItem(Material.TRIPWIRE_HOOK, ChatColor.GREEN + "Set Key",
            ChatColor.GRAY + "Click to configure how players",
            ChatColor.GRAY + "can obtain crate keys");

        ItemStack chanceItem = createGuiItem(Material.PAPER, ChatColor.GREEN + "Set Chances",
            ChatColor.GRAY + "Click to configure prize",
            ChatColor.GRAY + "winning chances");

        ItemStack saveItem = createGuiItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Save Crate",
            ChatColor.GRAY + "Click to save the crate configuration");

        ItemStack cancelItem = createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "Cancel",
            ChatColor.GRAY + "Click to cancel creation");

        gui.setItem(11, locationItem);
        gui.setItem(13, prizesItem);
        gui.setItem(15, keyItem);
        gui.setItem(22, chanceItem);
        gui.setItem(31, saveItem);
        gui.setItem(35, cancelItem);

        player.openInventory(gui);
    }

    private void openCrateEditGUI(Player player, String crateName) {
        Inventory gui = Bukkit.createInventory(null, 36, "Edit Crate: " + crateName);
        FileConfiguration config = getCrateConfig(crateName);
        
        Location loc = crateLocations.get(crateName);
        String locationText = loc != null ?
            String.format("Current: %.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()) :
            "Not set";
            
        ItemStack locationItem = createGuiItem(Material.COMPASS, ChatColor.GREEN + "Edit Location", 
            ChatColor.GRAY + locationText);
            
        ItemStack prizesItem = createGuiItem(Material.CHEST, ChatColor.GREEN + "Edit Prizes",
            ChatColor.GRAY + "Current prizes: " + (config.getList("prizes") != null ? 
                config.getList("prizes").size() : 0));
            
        ItemStack keyItem = createGuiItem(Material.TRIPWIRE_HOOK, ChatColor.GREEN + "Edit Key",
            ChatColor.GRAY + "Click to modify key settings");

        ItemStack chanceItem = createGuiItem(Material.PAPER, ChatColor.GREEN + "Edit Chances",
            ChatColor.GRAY + "Click to modify prize chances");

        ItemStack deleteItem = createGuiItem(Material.BARRIER, ChatColor.RED + "Delete Crate",
            ChatColor.GRAY + "Click to delete this crate",
            ChatColor.RED + "This action cannot be undone!");

        gui.setItem(11, locationItem);
        gui.setItem(13, prizesItem);
        gui.setItem(15, keyItem);
        gui.setItem(22, chanceItem);
        gui.setItem(35, deleteItem);

        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            
            List<String> loreList = new ArrayList<>();
            Collections.addAll(loreList, lore);
            meta.setLore(loreList);
            
            item.setItemMeta(meta);
        }
        return item;
    }

    private FileConfiguration getCrateConfig(String crateName) {
        return crateConfigs.computeIfAbsent(crateName, k -> {
            FileConfiguration config = new YamlConfiguration();
            try {
                File file = new File(cratesFolder, k + ".yml");
                if (file.exists()) {
                    config.load(file);
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error loading crate config: " + crateName, e);
            }
            return config;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith("Create Crate: ") && !title.startsWith("Edit Crate: ")) return;

        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        String crateName = title.split(": ")[1];
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        switch (clicked.getType()) {
            case COMPASS:
                setLocation(player, crateName);
                break;
            case CHEST:
                openPrizesGUI(player, crateName);
                break;
            case TRIPWIRE_HOOK:
                openKeySettingsGUI(player, crateName);
                break;
            case PAPER:
                openChancesGUI(player, crateName);
                break;
            case EMERALD_BLOCK:
                saveCrate(player, crateName);
                break;
            case REDSTONE_BLOCK:
                cancelCreation(player, crateName);
                break;
            case BARRIER:
                confirmDelete(player, crateName);
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        playerEditing.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location clickedLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
        
        if (clickedLoc != null) {
            for (Map.Entry<String, Location> entry : crateLocations.entrySet()) {
                if (clickedLoc.equals(entry.getValue())) {
                    event.setCancelled(true);
                    handleCrateInteraction(player, entry.getKey());
                    break;
                }
            }
        }
    }

    private void handleCrateInteraction(Player player, String crateName) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        FileConfiguration config = getCrateConfig(crateName);
        
        if (isValidKey(heldItem, crateName)) {
            openCrate(player, crateName);
            if (heldItem.getAmount() > 1) {
                heldItem.setAmount(heldItem.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            player.sendMessage(ChatColor.RED + "You need a valid key to open this crate!");
        }
    }

    private boolean isValidKey(ItemStack item, String crateName) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        
        String keyName = ChatColor.stripColor(meta.getDisplayName());
        return keyName.equals(crateName + " Key");
    }

    private void openCrate(Player player, String crateName) {
        player.sendMessage(ChatColor.GREEN + "Opening crate...");
    }

    private void setLocation(Player player, String crateName) {
        Location loc = player.getLocation();
        crateLocations.put(crateName, loc);
        
        FileConfiguration config = getCrateConfig(crateName);
        config.set("location", loc.serialize());
        
        player.sendMessage(ChatColor.GREEN + "Crate location set to your current position!");
        player.closeInventory();
    }

    private void openPrizesGUI(Player player, String crateName) {
        Inventory gui = Bukkit.createInventory(null, 54, "Configure Prizes: " + crateName);
        player.openInventory(gui);
    }

    private void openKeySettingsGUI(Player player, String crateName) {
        Inventory gui = Bukkit.createInventory(null, 27, "Configure Keys: " + crateName);
        player.openInventory(gui);
    }

    private void openChancesGUI(Player player, String crateName) {
        Inventory gui = Bukkit.createInventory(null, 36, "Configure Chances: " + crateName);
        player.openInventory(gui);
    }

    private void saveCrate(Player player, String crateName) {
        saveCrateConfig(crateName);
        player.sendMessage(ChatColor.GREEN + "Crate configuration saved successfully!");
        player.closeInventory();
    }

    private void cancelCreation(Player player, String crateName) {
        crateConfigs.remove(crateName);
        crateLocations.remove(crateName);
        player.sendMessage(ChatColor.RED + "Crate creation cancelled.");
        player.closeInventory();
    }

    private void confirmDelete(Player player, String crateName) {
        player.sendMessage(ChatColor.RED + "Type /crate delete " + crateName + " to confirm deletion.");
        player.closeInventory();
    }

    private void saveCrateConfig(String crateName) {
        try {
            File crateFile = new File(cratesFolder, crateName + ".yml");
            FileConfiguration config = getCrateConfig(crateName);
            config.save(crateFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save crate config: " + crateName, e);
        }
    }
}
