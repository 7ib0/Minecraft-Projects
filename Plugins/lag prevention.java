// prevents server lag by automatically detecting and clearing it also provides monitoring tools
package me.tibo.lagpreventionplugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.block.Hopper;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class LagPreventionPlugin extends JavaPlugin {
    private static final int MAX_ENTITIES_PER_CHUNK = 50;
    private static final int MAX_HOPPER_TRANSFERS = 100;
    private static final double TPS_THRESHOLD = 19.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        initializeEntityManager();
        initializeChunkManager();
        initializeRedstoneManager();
        initializeHopperManager();
        initializeTPSMonitor();
        initializeMemoryManager();
        initializePlayerManager();
        initializeWorldManager();
        initializePerformanceMonitor();
        initializeAntiLagMeasures();

        getCommand("lagprevention").setExecutor(new LagPreventionCommand());

        startMonitoringTasks();

        getLogger().info("LagPreventionPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LagPreventionPlugin has been disabled!");
    }

    private void initializeEntityManager() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : getServer().getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        int entityCount = chunk.getEntities().length;
                        if (entityCount > MAX_ENTITIES_PER_CHUNK) {
                            removeExcessEntities(chunk);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void removeExcessEntities(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        Arrays.sort(entities, (e1, e2) -> {
            return Double.compare(getNearestPlayerDistance(e1), getNearestPlayerDistance(e2));
        });

        for (int i = MAX_ENTITIES_PER_CHUNK; i < entities.length; i++) {
            entities[i].remove();
        }
    }

    private void initializeChunkManager() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : getServer().getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (isChunkInactive(chunk)) {
                            world.unloadChunk(chunk);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 600L, 600L);
    }

    private void initializeRedstoneManager() {
        getServer().getPluginManager().registerEvents(new RedstoneListener(), this);
    }

    private void initializeHopperManager() {
        getServer().getPluginManager().registerEvents(new HopperListener(), this);
    }

    private void initializeTPSMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double[] tps = getServer().getTPS();
                if (tps[0] < TPS_THRESHOLD) {
                    logTPSWarning(tps[0]);
                    takeCorrectiveAction();
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void initializeMemoryManager() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();

                if (usedMemory > maxMemory * 0.8) {
                    System.gc();
                    logMemoryWarning(usedMemory, maxMemory);
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    private void initializePlayerManager() {
        getServer().getPluginManager().registerEvents(new PlayerOptimizationListener(), this);
    }

    private void initializeWorldManager() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : getServer().getWorlds()) {
                    optimizeWorld(world);
                }
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void initializePerformanceMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                monitorServerPerformance();
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void initializeAntiLagMeasures() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndPerformMaintenance();
            }
        }.runTaskTimer(this, 72000L, 72000L);
    }

    private void startMonitoringTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePerformanceMetrics();
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private double getNearestPlayerDistance(Entity entity) {
        double minDistance = Double.MAX_VALUE;

        for (Entity nearby : entity.getNearbyEntities(100, 100, 100)) {
            if (nearby instanceof Player) {
                double distance = nearby.getLocation().distance(entity.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }

        return minDistance;
    }

    private boolean isChunkInactive(Chunk chunk) {
        if (chunk.getEntities().length > 0) {
            return false;
        }

        for (Player player : chunk.getWorld().getPlayers()) {
            if (player.getLocation().getChunk().equals(chunk)) {
                return false;
            }
        }

        return true;
    }

    private void logTPSWarning(double tps) {
        getLogger().warning("Low TPS detected: " + tps);
    }

    private void logMemoryWarning(long usedMemory, long maxMemory) {
        getLogger().warning("High memory usage: " + (usedMemory * 100 / maxMemory) + "%");
    }

    private void takeCorrectiveAction() {
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Player)) {
                    if (entity.getTicksLived() > 6000) {
                        entity.remove();
                    }
                }
            }
        }

        getServer().dispatchCommand(getServer().getConsoleSender(), "gc");
    }

    private void optimizeWorld(World world) {
        world.setAutoSave(false);
        world.save();
        world.setAutoSave(true);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && entity.getTicksLived() > 12000) {
                entity.remove();
            }
        }
    }

    private void monitorServerPerformance() {
        double[] tps = getServer().getTPS();
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int playerCount = getServer().getOnlinePlayers().size();

        int entityCount = 0;
        int chunkCount = 0;

        for (World world : getServer().getWorlds()) {
            entityCount += world.getEntities().size();
            chunkCount += world.getLoadedChunks().length;
        }

        logPerformanceData(tps[0], memoryUsed, playerCount, entityCount, chunkCount);
    }

    private void logPerformanceData(double tps, long memoryUsed, int playerCount, int entityCount, int chunkCount) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Performance: TPS=" + String.format("%.2f", tps) +
                    ", Memory=" + (memoryUsed / 1024 / 1024) + "MB" +
                    ", Players=" + playerCount +
                    ", Entities=" + entityCount +
                    ", Chunks=" + chunkCount);
        }
    }

    private void checkAndPerformMaintenance() {
        for (World world : getServer().getWorlds()) {
            world.save();
        }

        System.gc();

        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (isChunkInactive(chunk)) {
                    world.unloadChunk(chunk);
                }
            }
        }

        getLogger().info("Scheduled maintenance completed");
    }

    private void updatePerformanceMetrics() {
        double[] tps = getServer().getTPS();
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();

        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission("lagprevention.admin") && getConfig().getBoolean("show-metrics-to-admins", true)) {
                if (getConfig().getBoolean("actionbar-metrics", true)) {
                    sendActionBarMetrics(player, tps[0], memoryUsed, maxMemory);
                }
            }
        }
    }

    private void sendActionBarMetrics(Player player, double tps, long memoryUsed, long maxMemory) {
        String message = "§aTPS: " + String.format("%.2f", tps) + " §7| §aMemory: " +
                (memoryUsed / 1024 / 1024) + "MB/" + (maxMemory / 1024 / 1024) + "MB";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(message));
    }

    public class LagPreventionCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("lagprevention.command")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "status":
                    sendStatus(sender);
                    break;
                case "gc":
                    performGC(sender);
                    break;
                case "entities":
                    showEntities(sender);
                    break;
                case "chunks":
                    showChunks(sender);
                    break;
                case "clear":
                    clearEntities(sender);
                    break;
                case "reload":
                    reloadPluginConfig(sender);
                    break;
                default:
                    sendHelp(sender);
                    break;
            }

            return true;
        }

        private void sendHelp(CommandSender sender) {
            sender.sendMessage("§6===== §fLag Prevention Commands §6=====");
            sender.sendMessage("§e/lagprevention status §7- Show server performance status");
            sender.sendMessage("§e/lagprevention gc §7- Force garbage collection");
            sender.sendMessage("§e/lagprevention entities §7- Show entity counts by world");
            sender.sendMessage("§e/lagprevention chunks §7- Show loaded chunks by world");
            sender.sendMessage("§e/lagprevention clear §7- Clear non-player entities");
            sender.sendMessage("§e/lagprevention reload §7- Reload configuration");
        }

        private void sendStatus(CommandSender sender) {
            Runtime runtime = Runtime.getRuntime();
            long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double[] tps = getServer().getTPS();

            sender.sendMessage("§6===== §fServer Status §6=====");
            sender.sendMessage("§eTPS: §f" + String.format("%.2f", tps[0]));
            sender.sendMessage("§eMemory: §f" + (memoryUsed / 1024 / 1024) + "MB / " + (maxMemory / 1024 / 1024) + "MB");
            sender.sendMessage("§eOnline Players: §f" + getServer().getOnlinePlayers().size());

            int totalEntities = 0;
            int totalChunks = 0;

            for (World world : getServer().getWorlds()) {
                totalEntities += world.getEntities().size();
                totalChunks += world.getLoadedChunks().length;
            }

            sender.sendMessage("§eTotal Entities: §f" + totalEntities);
            sender.sendMessage("§eLoaded Chunks: §f" + totalChunks);
        }

        private void performGC(CommandSender sender) {
            long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            System.gc();

            long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long freed = beforeMemory - afterMemory;

            sender.sendMessage("§aGarbage collection completed.");
            sender.sendMessage("§eMemory freed: §f" + (freed / 1024 / 1024) + "MB");
        }

        private void showEntities(CommandSender sender) {
            sender.sendMessage("§6===== §fEntity Count By World §6=====");

            for (World world : getServer().getWorlds()) {
                int players = world.getPlayers().size();

                int animals = 0;
                int monsters = 0;
                int items = 0;
                int other = 0;

                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Animals) {
                        animals++;
                    } else if (entity instanceof Monster) {
                        monsters++;
                    } else if (entity instanceof Item) {
                        items++;
                    } else if (!(entity instanceof Player)) {
                        other++;
                    }
                }

                sender.sendMessage("§e" + world.getName() + ": §fTotal: " + world.getEntities().size());
                sender.sendMessage("  §7Players: " + players + ", Animals: " + animals + ", Monsters: " + monsters);
                sender.sendMessage("  §7Items: " + items + ", Other: " + other);
            }
        }

        private void showChunks(CommandSender sender) {
            sender.sendMessage("§6===== §fLoaded Chunks By World §6=====");

            for (World world : getServer().getWorlds()) {
                sender.sendMessage("§e" + world.getName() + ": §f" + world.getLoadedChunks().length + " chunks");
            }
        }

        private void clearEntities(CommandSender sender) {
            int count = 0;

            for (World world : getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Player)) {
                        entity.remove();
                        count++;
                    }
                }
            }

            sender.sendMessage("§aRemoved " + count + " entities from all worlds.");
        }

        private void reloadPluginConfig(CommandSender sender) {
            reloadConfig();
            sender.sendMessage("§aConfiguration reloaded successfully.");
        }
    }

    public class RedstoneListener implements Listener {
        private final Map<Location, Integer> redstoneActivityMap = new HashMap<>();
        private final int MAX_REDSTONE_ACTIVITY = 100;

        public RedstoneListener() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    redstoneActivityMap.clear();
                }
            }.runTaskTimer(LagPreventionPlugin.this, 600L, 600L);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onRedstoneUpdate(BlockRedstoneEvent event) {
            Location location = event.getBlock().getLocation();
            int activity = redstoneActivityMap.getOrDefault(location, 0) + 1;
            redstoneActivityMap.put(location, activity);

            if (activity > MAX_REDSTONE_ACTIVITY && getConfig().getBoolean("limit-redstone", true)) {
                event.setNewCurrent(0);

                if (getConfig().getBoolean("debug", false)) {
                    getLogger().info("Blocked excessive redstone at " +
                            formatLocation(location));
                }
            }
        }

        private String formatLocation(Location loc) {
            return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " +
                    loc.getBlockY() + ", " + loc.getBlockZ() + ")";
        }
    }

    public class HopperListener implements Listener {
        private final Map<Location, Integer> hopperActivityMap = new HashMap<>();

        public HopperListener() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    hopperActivityMap.clear();
                }
            }.runTaskTimer(LagPreventionPlugin.this, 1200L, 1200L);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onInventoryMoveItem(InventoryMoveItemEvent event) {
            if (event.getSource().getHolder() instanceof Hopper ||
                    event.getDestination().getHolder() instanceof Hopper) {

                Location location = null;

                if (event.getSource().getHolder() instanceof Hopper) {
                    location = ((Hopper) event.getSource().getHolder()).getLocation();
                } else {
                    location = ((Hopper) event.getDestination().getHolder()).getLocation();
                }

                int activity = hopperActivityMap.getOrDefault(location, 0) + 1;
                hopperActivityMap.put(location, activity);

                if (activity > getConfig().getInt("max-hopper-transfers", 100) &&
                        getConfig().getBoolean("limit-hoppers", true)) {
                    event.setCancelled(true);

                    if (getConfig().getBoolean("debug", false)) {
                        getLogger().info("Blocked excessive hopper transfer at " +
                                formatLocation(location));
                    }
                }
            }
        }

        private String formatLocation(Location loc) {
            return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " +
                    loc.getBlockY() + ", " + loc.getBlockZ() + ")";
        }
    }

    public class PlayerOptimizationListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();

            if (getConfig().getBoolean("optimize-view-distance", true)) {
                int viewDistance = getConfig().getInt("player-view-distance", 8);
                player.setViewDistance(viewDistance);
            }

            if (player.hasPermission("lagprevention.admin")) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sendWelcomeMessage(player);
                    }
                }.runTaskLater(LagPreventionPlugin.this, 40L);
            }
        }

        @EventHandler
        public void onPlayerMove(PlayerMoveEvent event) {
            if (getConfig().getBoolean("chunk-loading-optimization", true)) {
                Location from = event.getFrom();
                Location to = event.getTo();

                if (from != null && to != null && from.getChunk() != to.getChunk()) {
                    optimizeChunksAroundPlayer(event.getPlayer());
                }
            }
        }

        private void optimizeChunksAroundPlayer(Player player) {
            int loadRadius = getConfig().getInt("chunk-load-radius", 8);
            int unloadRadius = loadRadius + 2;

            Location playerLoc = player.getLocation();
            int playerChunkX = playerLoc.getBlockX() >> 4;
            int playerChunkZ = playerLoc.getBlockZ() >> 4;

            for (Chunk chunk : player.getWorld().getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();

                int distanceSquared = (playerChunkX - chunkX) * (playerChunkX - chunkX) +
                        (playerChunkZ - chunkZ) * (playerChunkZ - chunkZ);

                if (distanceSquared > unloadRadius * unloadRadius) {
                    if (isChunkSafeToUnload(chunk)) {
                        player.getWorld().unloadChunk(chunkX, chunkZ);
                    }
                }
            }
        }

        private boolean isChunkSafeToUnload(Chunk chunk) {
            for (Player player : chunk.getWorld().getPlayers()) {
                int playerChunkX = player.getLocation().getBlockX() >> 4;
                int playerChunkZ = player.getLocation().getBlockZ() >> 4;

                int distanceSquared = (playerChunkX - chunk.getX()) * (playerChunkX - chunk.getX()) +
                        (playerChunkZ - chunk.getZ()) * (playerChunkZ - chunk.getZ());

                if (distanceSquared <= 64) { // 8 chunks
                    return false;
                }
            }

            return true;
        }

        private void sendWelcomeMessage(Player player) {
            player.sendMessage("§6===== §fLag Prevention Status §6=====");
            double[] tps = getServer().getTPS();
            player.sendMessage("§eTPS: §f" + String.format("%.2f", tps[0]));
            player.sendMessage("§eUse §f/lagprevention status §efor more details");
        }
    }
}
