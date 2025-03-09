// Look in Tibo-skript channel for the showcase of this plugin.
package me.tibo.mines;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;

import java.util.*;

public class Mines extends JavaPlugin implements Listener {

    private Map<String, Mine> mines = new HashMap<>();
    private Map<String, MineSelection> selections = new HashMap<>();
    private FileConfiguration config;
    private Set<UUID> awaitingMineInput = new HashSet<>();
    private Map<UUID, String> awaitingIntervalInput = new HashMap<>();
    private Map<String, ArmorStand> countdownDisplays = new HashMap<>();

    private class MineSelection {
        Location[] positions = new Location[2];
        Map<Material, Double> composition;

        public MineSelection(Map<Material, Double> composition) {
            this.composition = composition;
        }

        public boolean isComplete() {
            return positions[0] != null && positions[1] != null;
        }

        public void setPosition(Location loc, int index) {
            positions[index] = loc;
        }

        public Mine createMine(String name) {
            return new Mine(name, positions[0], positions[1], composition);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        config.addDefault("wand-item", "GOLDEN_AXE");
        config.addDefault("countdown-height", 0.5);
        config.options().copyDefaults(true);
        saveConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("mine").setExecutor(new MineCommand());

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Mine mine : mines.values()) {
                if (mine.autoReset && mine.showCountdown) {
                    updateCountdownDisplay(mine);
                }
            }
        }, 20L, 20L);
    }

    private class Mine {
        String name;
        Location pos1;
        Location pos2;
        Map<Material, Double> composition;
        boolean autoReset;
        boolean showCountdown;
        int resetMinutes;
        long lastReset;

        public Mine(String name, Location pos1, Location pos2, Map<Material, Double> composition) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.composition = composition;
            this.autoReset = false;
            this.showCountdown = true;
            this.resetMinutes = 30;
            this.lastReset = System.currentTimeMillis();
        }

        public void generate() {
            int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
            int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
            int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
            int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

            Random random = new Random();

            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        double rand = random.nextDouble();
                        double cumulative = 0.0;

                        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
                            cumulative += entry.getValue();
                            if (rand <= cumulative) {
                                pos1.getWorld().getBlockAt(x, y, z).setType(entry.getKey());
                                break;
                            }
                        }
                    }
                }
            }

            lastReset = System.currentTimeMillis();
            createOrUpdateDisplay();
        }

        private void createOrUpdateDisplay() {
            if (!showCountdown || !autoReset) {
                ArmorStand display = countdownDisplays.remove(name);
                if (display != null) {
                    display.remove();
                }
                return;
            }

            Location center = new Location(pos1.getWorld(),
                    (pos1.getX() + pos2.getX()) / 2,
                    Math.max(pos1.getY(), pos2.getY()) + config.getDouble("countdown-height"),
                    (pos1.getZ() + pos2.getZ()) / 2);

            ArmorStand display = countdownDisplays.get(name);
            if (display == null || !display.isValid()) {
                display = pos1.getWorld().spawn(center, ArmorStand.class);
                display.setVisible(false);
                display.setGravity(false);
                display.setCustomNameVisible(true);
                countdownDisplays.put(name, display);
            } else {
                display.teleport(center);
            }
        }
    }

    private void updateCountdownDisplay(Mine mine) {
        ArmorStand display = countdownDisplays.get(mine.name);
        if (display == null || !display.isValid()) {
            mine.createOrUpdateDisplay();
            display = countdownDisplays.get(mine.name);
        }

        if (!mine.autoReset || !mine.showCountdown) {
            display.remove();
            countdownDisplays.remove(mine.name);
            return;
        }

        long timeLeft = (mine.lastReset + (mine.resetMinutes * 60 * 1000)) - System.currentTimeMillis();
        if (timeLeft <= 0) {
            mine.generate();
            return;
        }

        int seconds = (int) (timeLeft / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;

        display.setCustomName("§e" + mine.name + " §7- §f" +
                String.format("%02d:%02d", minutes, seconds));
    }

    private void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Mine Manager");

        ItemStack createMine = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta createMeta = createMine.getItemMeta();
        createMeta.setDisplayName("§a§lCreate New Mine");
        createMine.setItemMeta(createMeta);

        ItemStack configMine = new ItemStack(Material.REDSTONE);
        ItemMeta configMeta = configMine.getItemMeta();
        configMeta.setDisplayName("§6§lConfigure Mines");
        configMine.setItemMeta(configMeta);

        ItemStack deleteMine = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = deleteMine.getItemMeta();
        deleteMeta.setDisplayName("§c§lDelete Mine");
        deleteMine.setItemMeta(deleteMeta);

        gui.setItem(11, createMine);
        gui.setItem(13, configMine);
        gui.setItem(15, deleteMine);

        player.openInventory(gui);
    }

    private class MineCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;
            openMainMenu(player);
            return true;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getView().getTitle().equals("Mine Manager")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            switch(event.getCurrentItem().getType()) {
                case DIAMOND_PICKAXE:
                    player.closeInventory();
                    awaitingMineInput.add(player.getUniqueId());
                    player.sendMessage("§aEnter mine name and composition in chat:");
                    player.sendMessage("§7Format: <name> <composition>");
                    player.sendMessage("§7Example: mymine 50%stone,30%dirt,20%iron_ore");
                    break;

                case REDSTONE:
                    if (mines.isEmpty()) {
                        player.sendMessage("§cNo mines exist yet!");
                        return;
                    }
                    openConfigMenu(player);
                    break;

                case BARRIER:
                    if (mines.isEmpty()) {
                        player.sendMessage("§cNo mines exist yet!");
                        return;
                    }
                    openDeleteMenu(player);
                    break;
            }
        } else if (event.getView().getTitle().equals("Configure Mines")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String mineName = event.getCurrentItem().getItemMeta().getDisplayName().substring(2);
            openMineSettingsMenu(player, mineName);
        } else if (event.getView().getTitle().startsWith("Settings: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String mineName = event.getView().getTitle().substring(10);
            Mine mine = mines.get(mineName);

            switch(event.getCurrentItem().getType()) {
                case REDSTONE_TORCH:
                    mine.autoReset = !mine.autoReset;
                    mine.createOrUpdateDisplay();
                    openMineSettingsMenu(player, mineName);
                    break;

                case CLOCK:
                    player.closeInventory();
                    awaitingIntervalInput.put(player.getUniqueId(), mineName);
                    player.sendMessage("§aEnter new reset interval in minutes:");
                    break;

                case DIAMOND_PICKAXE:
                    mine.generate();
                    player.sendMessage("§aMine has been reset!");
                    break;

                case BARRIER:
                    openConfigMenu(player);
                    break;

                case NAME_TAG:
                    mine.showCountdown = !mine.showCountdown;
                    mine.createOrUpdateDisplay();
                    openMineSettingsMenu(player, mineName);
                    break;
            }
        } else if (event.getView().getTitle().equals("Delete Mines")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String mineName = event.getCurrentItem().getItemMeta().getDisplayName().substring(2);
            ArmorStand display = countdownDisplays.remove(mineName);
            if (display != null) {
                display.remove();
            }
            mines.remove(mineName);
            player.sendMessage("§aMine '" + mineName + "' has been deleted!");
            openDeleteMenu(player);
        }
    }

    private void openMineSettingsMenu(Player player, String mineName) {
        Mine mine = mines.get(mineName);
        Inventory gui = Bukkit.createInventory(null, 27, "Settings: " + mineName);

        ItemStack autoReset = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta autoResetMeta = autoReset.getItemMeta();
        autoResetMeta.setDisplayName((mine.autoReset ? "§a" : "§c") + "Auto Reset");
        autoResetMeta.setLore(Arrays.asList("§7Current: " + (mine.autoReset ? "§aEnabled" : "§cDisabled")));
        autoReset.setItemMeta(autoResetMeta);

        ItemStack interval = new ItemStack(Material.CLOCK);
        ItemMeta intervalMeta = interval.getItemMeta();
        intervalMeta.setDisplayName("§eReset Interval");
        intervalMeta.setLore(Arrays.asList("§7Current: §b" + mine.resetMinutes + " minutes"));
        interval.setItemMeta(intervalMeta);

        ItemStack reset = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta resetMeta = reset.getItemMeta();
        resetMeta.setDisplayName("§bReset Mine Now");
        reset.setItemMeta(resetMeta);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack");
        back.setItemMeta(backMeta);

        ItemStack countdown = new ItemStack(Material.NAME_TAG);
        ItemMeta countdownMeta = countdown.getItemMeta();
        countdownMeta.setDisplayName((mine.showCountdown ? "§a" : "§c") + "Show Countdown");
        countdownMeta.setLore(Arrays.asList("§7Current: " + (mine.showCountdown ? "§aEnabled" : "§cDisabled")));
        countdown.setItemMeta(countdownMeta);

        gui.setItem(10, autoReset);
        gui.setItem(12, interval);
        gui.setItem(14, reset);
        gui.setItem(16, countdown);
        gui.setItem(26, back);

        player.openInventory(gui);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingMineInput.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String[] parts = event.getMessage().split(" ");

            if (parts.length != 2) {
                player.sendMessage("§cInvalid format! Use: <name> <composition>");
                return;
            }

            String mineName = parts[0];
            String compositionStr = parts[1];
            Map<Material, Double> composition = new HashMap<>();
            double total = 0.0;

            try {
                for (String block : compositionStr.split(",")) {
                    String[] blockParts = block.split("%");
                    if (blockParts.length != 2) {
                        throw new IllegalArgumentException("Invalid block format");
                    }

                    double percentage = Double.parseDouble(blockParts[0]) / 100.0;
                    Material material = Material.valueOf(blockParts[1].toUpperCase());

                    composition.put(material, percentage);
                    total += percentage;
                }

                if (Math.abs(total - 1.0) > 0.001) {
                    player.sendMessage("§cPercentages must add up to 100%!");
                    return;
                }

                Material wandMaterial = Material.valueOf(config.getString("wand-item", "GOLDEN_AXE").toUpperCase());
                ItemStack selector = new ItemStack(wandMaterial);
                ItemMeta meta = selector.getItemMeta();
                meta.setDisplayName("Mine Selector - " + mineName);
                selector.setItemMeta(meta);

                player.getInventory().addItem(selector);
                selections.put(mineName, new MineSelection(composition));
                awaitingMineInput.remove(player.getUniqueId());

                player.sendMessage("§aUse the wand to select the mine area!");
                player.sendMessage("§7Left/Right click: Set positions");

            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid block type or percentage format!");
                return;
            }
        } else if (awaitingIntervalInput.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            try {
                int minutes = Integer.parseInt(event.getMessage());
                if (minutes <= 0) {
                    player.sendMessage("§cInterval must be greater than 0!");
                    return;
                }

                String mineName = awaitingIntervalInput.remove(player.getUniqueId());
                Mine mine = mines.get(mineName);
                mine.resetMinutes = minutes;

                player.sendMessage("§aReset interval updated to " + minutes + " minutes!");
                openMineSettingsMenu(player, mineName);
            } catch (NumberFormatException e) {
                player.sendMessage("§cPlease enter a valid number!");
            }
        }
    }

    private void openConfigMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "Configure Mines");
        int slot = 0;

        for (String mineName : mines.keySet()) {
            Mine mine = mines.get(mineName);
            ItemStack mineItem = new ItemStack(Material.CHEST);
            ItemMeta meta = mineItem.getItemMeta();
            meta.setDisplayName("§e" + mineName);
            List<String> lore = Arrays.asList(
                    "§7Auto Reset: " + (mine.autoReset ? "§aEnabled" : "§cDisabled"),
                    "§7Reset Interval: §b" + mine.resetMinutes + " minutes",
                    "§7Show Countdown: " + (mine.showCountdown ? "§aEnabled" : "§cDisabled"),
                    "",
                    "§7Click to configure"
            );
            meta.setLore(lore);
            mineItem.setItemMeta(meta);
            gui.setItem(slot++, mineItem);
        }

        player.openInventory(gui);
    }

    private void openDeleteMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "Delete Mines");
        int slot = 0;

        for (String mineName : mines.keySet()) {
            ItemStack mineItem = new ItemStack(Material.RED_CONCRETE);
            ItemMeta meta = mineItem.getItemMeta();
            meta.setDisplayName("§c" + mineName);
            List<String> lore = Arrays.asList("§7Click to delete");
            meta.setLore(lore);
            mineItem.setItemMeta(meta);
            gui.setItem(slot++, mineItem);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        Material wandMaterial = Material.valueOf(config.getString("wand-item", "GOLDEN_AXE").toUpperCase());
        if (item.getType() == wandMaterial && item.hasItemMeta()) {
            String itemName = item.getItemMeta().getDisplayName();
            if (itemName.startsWith("Mine Selector - ")) {
                event.setCancelled(true);
                String mineName = itemName.substring("Mine Selector - ".length());
                MineSelection selection = selections.get(mineName);

                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    Block clickedBlock = event.getClickedBlock();
                    if (clickedBlock == null) return;

                    int posIndex = selection.positions[0] == null ? 0 :
                            selection.positions[1] == null ? 1 : 0;

                    selection.setPosition(clickedBlock.getLocation(), posIndex);
                    player.sendMessage("§aPosition " + (posIndex + 1) + " set");

                    if (selection.isComplete()) {
                        Mine mine = selection.createMine(mineName);
                        mines.put(mineName, mine);
                        mine.generate();

                        player.sendMessage("§a§lMine created successfully!");
                        selections.remove(mineName);
                    }
                }
            }
        }
    }
}
