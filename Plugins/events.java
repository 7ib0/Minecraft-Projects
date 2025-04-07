package me.tibo.events;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Events extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private File configFile;
    private Location spleefArea;
    private Location ffaArea;
    private List<UUID> eventPlayers;
    private boolean eventActive;
    private String currentEvent;
    private int minPlayers;
    private int maxPlayers;
    private int countdown;
    private boolean countdownStarted;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        configFile = new File(getDataFolder(), "eventareas.yml");
        if (!configFile.exists()) {
            saveResource("eventareas.yml", false);
        }
        eventPlayers = new ArrayList<>();
        eventActive = false;
        countdownStarted = false;
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("hostevent").setExecutor(this);
        getCommand("eventconfigurearea").setExecutor(this);
        getCommand("joinevent").setExecutor(this);
        getCommand("leavetvent").setExecutor(this);
        loadConfig();
    }

    private void loadConfig() {
        minPlayers = config.getInt("min_players", 2);
        maxPlayers = config.getInt("max_players", 16);
        countdown = config.getInt("countdown_seconds", 30);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        switch(cmd.getName().toLowerCase()) {
            case "hostevent":
                if (!player.isOp()) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                openEventGUI(player);
                break;
            case "eventconfigurearea":
                if (!player.isOp()) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                if (args.length < 1) {
                    player.sendMessage("§cUsage: /eventconfigurearea <spleef|ffa>");
                    return true;
                }
                configureEventArea(player, args[0]);
                break;
            case "joinevent":
                joinEvent(player);
                break;
            case "leavetvent":
                leaveEvent(player);
                break;
        }
        return true;
    }

    private void openEventGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6Event Selection");
        ItemStack spleef = createGuiItem(Material.DIAMOND_SHOVEL, "§bSpleef Event", "§7Click to start a Spleef event!");
        ItemStack ffa = createGuiItem(Material.DIAMOND_SWORD, "§cFFA Event", "§7Click to start a Free-For-All event!");
        gui.setItem(11, spleef);
        gui.setItem(15, ffa);
        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6Event Selection")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getItemMeta().getDisplayName().equals("§bSpleef Event")) {
            startSpleefEvent(player);
        } else if (clicked.getItemMeta().getDisplayName().equals("§cFFA Event")) {
            startFFAEvent(player);
        }
    }

    private void configureEventArea(Player player, String eventType) {
        Location loc = player.getLocation();

        if (eventType.equalsIgnoreCase("spleef")) {
            spleefArea = loc;
            player.sendMessage("§aSpleef area configured!");
            createSpleefArena(loc);
        } else if (eventType.equalsIgnoreCase("ffa")) {
            ffaArea = loc;
            player.sendMessage("§aFFA area configured!");
            createFFAArena(loc);
        }
    }

    private void createSpleefArena(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                world.getBlockAt(x + i, y, z + j).setType(Material.SNOW_BLOCK);
            }
        }

        for (int i = -10; i <= 10; i++) {
            for (int h = 0; h <= 3; h++) {
                world.getBlockAt(x + i, y + h, z - 10).setType(Material.GLASS);
                world.getBlockAt(x + i, y + h, z + 10).setType(Material.GLASS);
                world.getBlockAt(x - 10, y + h, z + i).setType(Material.GLASS);
                world.getBlockAt(x + 10, y + h, z + i).setType(Material.GLASS);
            }
        }

        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                world.getBlockAt(x + i, y - 1, z + j).setType(Material.LAVA);
            }
        }
    }

    private void createFFAArena(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (int i = -15; i <= 15; i++) {
            for (int j = -15; j <= 15; j++) {
                world.getBlockAt(x + i, y, z + j).setType(Material.STONE);
            }
        }

        for (int i = -15; i <= 15; i++) {
            for (int h = 0; h <= 5; h++) {
                world.getBlockAt(x + i, y + h, z - 15).setType(Material.BARRIER);
                world.getBlockAt(x + i, y + h, z + 15).setType(Material.BARRIER);
                world.getBlockAt(x - 15, y + h, z + i).setType(Material.BARRIER);
                world.getBlockAt(x + 15, y + h, z + i).setType(Material.BARRIER);
            }
        }
    }

    private void startSpleefEvent(Player host) {
        if (spleefArea == null) {
            host.sendMessage("§cSpleef area isnt configured! Use /eventconfigurearea spleef first.");
            return;
        }

        currentEvent = "spleef";
        eventActive = true;
        Bukkit.broadcastMessage("§6§lSPLEEF EVENT STARTING!");
        Bukkit.broadcastMessage("§eUse /joinevent to participate!");

        host.teleport(spleefArea);
        host.getInventory().clear();
        host.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL));

        startCountdown();
    }

    private void startFFAEvent(Player host) {
        if (ffaArea == null) {
            host.sendMessage("§cFFA area isn't configured! Use /eventconfigurearea ffa first.");
            return;
        }

        currentEvent = "ffa";
        eventActive = true;
        Bukkit.broadcastMessage("§c§lFREE-FOR-ALL EVENT STARTING!");
        Bukkit.broadcastMessage("§eUse /joinevent to participate!");

        host.teleport(ffaArea);
        host.getInventory().clear();
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 2);  // Using Enchantment.DAMAGE_ALL instead of SHARPNESS
        host.getInventory().addItem(sword);

        startCountdown();
    }

    private void joinEvent(Player player) {
        if (!eventActive) {
            player.sendMessage("§cThere is no event currently active!");
            return;
        }

        if (eventPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cYou are already in the event!");
            return;
        }

        if (eventPlayers.size() >= maxPlayers) {
            player.sendMessage("§cThe event is full!");
            return;
        }

        eventPlayers.add(player.getUniqueId());
        player.sendMessage("§aYou have joined the event!");

        if (currentEvent.equals("spleef")) {
            player.teleport(spleefArea);
            player.getInventory().clear();
            ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
            shovel.addEnchantment(Enchantment.EFFICIENCY, 5);  // Using Enchantment.DIG_SPEED instead of EFFICIENCY 
            player.getInventory().addItem(shovel);
        } else if (currentEvent.equals("ffa")) {
            player.teleport(ffaArea);
            player.getInventory().clear();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            sword.addEnchantment(Enchantment.SHARPNESS, 2);  // Using Enchantment.DAMAGE_ALL instead of SHARPNESS
            player.getInventory().addItem(sword);
            player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        }

        Bukkit.broadcastMessage("§e" + player.getName() + " has joined the event! (" + eventPlayers.size() + "/" + maxPlayers + ")");

        if (eventPlayers.size() >= minPlayers && !countdownStarted) {
            startCountdown();
        }
    }

    private void leaveEvent(Player player) {
        if (!eventPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cYou are not in any event!");
            return;
        }

        eventPlayers.remove(player.getUniqueId());
        player.sendMessage("§aYou have left the event!");
        player.getInventory().clear();
        player.teleport(player.getWorld().getSpawnLocation());

        Bukkit.broadcastMessage("§e" + player.getName() + " has left the event! (" + eventPlayers.size() + "/" + maxPlayers + ")");

        if (eventPlayers.size() < minPlayers && countdownStarted) {
            Bukkit.broadcastMessage("§cNot enough players! Countdown stopped.");
            countdownStarted = false;
        }

        if (eventPlayers.isEmpty() && eventActive) {
            endEvent();
        }
    }

    private void startCountdown() {
        if (countdownStarted) return;

        countdownStarted = true;

        new BukkitRunnable() {
            int timeLeft = countdown;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    Bukkit.broadcastMessage("§a§lEVENT STARTED!");

                    if (currentEvent.equals("spleef")) {
                        startSpleefGame();
                    } else if (currentEvent.equals("ffa")) {
                        startFFAGame();
                    }

                    cancel();
                    return;
                }

                if (timeLeft == 60 || timeLeft == 30 || timeLeft == 15 || timeLeft <= 5) {
                    Bukkit.broadcastMessage("§e§lEvent starting in " + timeLeft + " seconds!");
                }

                timeLeft--;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void startSpleefGame() {
        for (UUID playerId : eventPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(spleefArea);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1));
            }
        }

        // Monitor player deaths
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive || !currentEvent.equals("spleef")) {
                    cancel();
                    return;
                }

                List<UUID> toRemove = new ArrayList<>();

                for (UUID playerId : eventPlayers) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || player.getLocation().getY() < spleefArea.getY() - 5) {
                        toRemove.add(playerId);
                        if (player != null) {
                            player.teleport(player.getWorld().getSpawnLocation());
                            player.sendMessage("§cYou have been eliminated!");
                            player.getInventory().clear();
                        }
                    }
                }

                for (UUID id : toRemove) {
                    eventPlayers.remove(id);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        Bukkit.broadcastMessage("§e" + player.getName() + " has been eliminated! (" + eventPlayers.size() + " remaining)");
                    }
                }

                if (eventPlayers.size() <= 1) {
                    if (eventPlayers.size() == 1) {
                        Player winner = Bukkit.getPlayer(eventPlayers.get(0));
                        if (winner != null) {
                            Bukkit.broadcastMessage("§6§l" + winner.getName() + " has won the Spleef event!");
                        }
                    }
                    endEvent();
                    cancel();
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void startFFAGame() {
        for (UUID playerId : eventPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(ffaArea);
                player.setHealth(20);
                player.setFoodLevel(20);
            }
        }

        // Monitor player deaths
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive || !currentEvent.equals("ffa")) {
                    cancel();
                    return;
                }

                if (eventPlayers.size() <= 1) {
                    if (eventPlayers.size() == 1) {
                        Player winner = Bukkit.getPlayer(eventPlayers.get(0));
                        if (winner != null) {
                            Bukkit.broadcastMessage("§6§l" + winner.getName() + " has won the FFA event!");
                        }
                    }
                    endEvent();
                    cancel();
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (eventActive && eventPlayers.contains(player.getUniqueId())) {
            event.setKeepInventory(true);
            eventPlayers.remove(player.getUniqueId());
            player.getInventory().clear();
            player.spigot().respawn();
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§cYou have been eliminated!");

            Bukkit.broadcastMessage("§e" + player.getName() + " has been eliminated! (" + eventPlayers.size() + " remaining)");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (eventActive && currentEvent.equals("spleef") && eventPlayers.contains(player.getUniqueId())) {
            if (event.getBlock().getType() != Material.SNOW_BLOCK) {
                event.setCancelled(true);
            }
        } else if (eventActive && eventPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (eventActive && eventPlayers.contains(player.getUniqueId())) {
            eventPlayers.remove(player.getUniqueId());
            Bukkit.broadcastMessage("§e" + player.getName() + " has left the event! (" + eventPlayers.size() + " remaining)");

            if (eventPlayers.isEmpty()) {
                endEvent();
            }
        }
    }

    private void endEvent() {
        eventActive = false;
        countdownStarted = false;
        currentEvent = null;

        for (UUID playerId : eventPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(player.getWorld().getSpawnLocation());
                player.getInventory().clear();
                player.setGameMode(GameMode.SURVIVAL);

                // Remove any potion effects
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
            }
        }

        eventPlayers.clear();
        Bukkit.broadcastMessage("§c§lEvent has ended!");
    }
}
