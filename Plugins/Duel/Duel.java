package me.tibo.duel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Duel extends JavaPlugin implements Listener {

    private File kitsFile;
    private FileConfiguration kitsConfig;
    private File leaderboardFile;
    private FileConfiguration leaderboardConfig;
    private Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();
    private Map<UUID, DuelSession> activeDuels = new ConcurrentHashMap<>();
    private Map<UUID, Integer> duelWins = new ConcurrentHashMap<>();
    private Map<UUID, Boolean> inDuelMenu = new ConcurrentHashMap<>();
    private Arena arena;
    private Map<String, Kit> kits = new HashMap<>();
    private Map<UUID, BukkitRunnable> duelRequestTimeouts = new ConcurrentHashMap<>();

    private final List<DuelSession> duelSessions = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadArena();
        loadKits();
        loadLeaderboard();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand duelCmd = getCommand("duel");
        if (duelCmd != null) {
            duelCmd.setExecutor(new DuelCommand());
        } else {
            getLogger().severe("Failed to register /duel command! Check plugin.yml.");
        }
        PluginCommand duelLbCmd = getCommand("duel-leaderboard");
        if (duelLbCmd != null) {
            duelLbCmd.setExecutor(new LeaderboardCommand());
        }
        PluginCommand duelLbShortCmd = getCommand("duel-lb");
        if (duelLbShortCmd != null) {
            duelLbShortCmd.setExecutor(new LeaderboardCommand());
        }
        PluginCommand duelStatsCmd = getCommand("duel-stats");
        if (duelStatsCmd != null) {
            duelStatsCmd.setExecutor(new DuelStatsCommand());
        }
        PluginCommand duelSpectateCmd = getCommand("spectate");
        if (duelSpectateCmd != null) {
            duelSpectateCmd.setExecutor(new SpectateCommand());
        }
    }

    @Override
    public void onDisable() {
        saveLeaderboard();
        HandlerList.unregisterAll((Listener) this);
        for (BukkitRunnable task : duelRequestTimeouts.values()) {
            task.cancel();
        }
        duelRequestTimeouts.clear();
        duelSessions.clear();
    }

    private void loadArena() {
        FileConfiguration config = getConfig();
        if (!config.contains("arenas.bedrock_box.world")) {
            config.set("arenas.bedrock_box.world", "world");
            config.set("arenas.bedrock_box.corner1", Arrays.asList(100, 70, 100));
            config.set("arenas.bedrock_box.corner2", Arrays.asList(114, 75, 114));
            saveConfig();
        }
        String world = config.getString("arenas.bedrock_box.world");
        List<Integer> c1 = config.getIntegerList("arenas.bedrock_box.corner1");
        List<Integer> c2 = config.getIntegerList("arenas.bedrock_box.corner2");
        arena = new Arena(world, c1, c2);
    }

    private void loadKits() {
        kitsFile = new File(getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            try {
                kitsFile.getParentFile().mkdirs();
                kitsFile.createNewFile();
                YamlConfiguration def = new YamlConfiguration();
                def.set("kits.cpvp.icon", "DIAMOND_SWORD");
                def.set("kits.cpvp.items", Arrays.asList(
                        "DIAMOND_SWORD",
                        "DIAMOND_HELMET",
                        "DIAMOND_CHESTPLATE",
                        "DIAMOND_LEGGINGS",
                        "DIAMOND_BOOTS",
                        "END_CRYSTAL*64",
                        "END_CRYSTAL*64",
                        "OBSIDIAN*64",
                        "OBSIDIAN*64",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "TOTEM_OF_UNDYING*1",
                        "GOLDEN_APPLE*64",
                        "RESPAWN_ANCHOR*64",
                        "GLOWSTONE*64",
                        "DIAMOND_PICKAXE",
                        "SHIELD",
                        "BOW",
                        "ARROW*64"
                ));
                def.set("kits.swordpvp.icon", "IRON_SWORD");
                def.set("kits.swordpvp.items", Arrays.asList(
                        "IRON_SWORD",
                        "IRON_HELMET",
                        "IRON_CHESTPLATE",
                        "IRON_LEGGINGS",
                        "IRON_BOOTS",
                        "GOLDEN_APPLE*64"
                ));
                def.save(kitsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        kits.clear();
        if (kitsConfig.contains("kits")) {
            ConfigurationSection section = kitsConfig.getConfigurationSection("kits");
            for (String kitName : section.getKeys(false)) {
                String iconName = section.getString(kitName + ".icon", "STONE");
                List<String> itemList = section.getStringList(kitName + ".items");
                Kit kit = new Kit(kitName, iconName, itemList);
                kits.put(kitName.toLowerCase(), kit);
            }
        }
    }

    private void loadLeaderboard() {
        leaderboardFile = new File(getDataFolder(), "leaderboard.yml");
        if (!leaderboardFile.exists()) {
            try {
                leaderboardFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);
        duelWins.clear();
        if (leaderboardConfig.contains("wins")) {
            ConfigurationSection section = leaderboardConfig.getConfigurationSection("wins");
            for (String uuid : section.getKeys(false)) {
                duelWins.put(UUID.fromString(uuid), leaderboardConfig.getInt("wins." + uuid));
            }
        }
    }

    private void saveLeaderboard() {
        for (Map.Entry<UUID, Integer> entry : duelWins.entrySet()) {
            leaderboardConfig.set("wins." + entry.getKey().toString(), entry.getValue());
        }
        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void incrementWin(UUID uuid) {
        duelWins.put(uuid, duelWins.getOrDefault(uuid, 0) + 1);
        saveLeaderboard();
    }

    private boolean isInDuel(Player p) {
        return activeDuels.containsKey(p.getUniqueId());
    }

    private boolean isInRequest(Player p) {
        return pendingRequests.containsKey(p.getUniqueId());
    }

    private void openKitMenu(Player challenger, Player target) {
        int size = Math.max(9, 9 * ((kits.size() + 2 + 8) / 9));
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.AQUA + "Select a Kit");
        for (Kit kit : kits.values()) {
            ItemStack icon = kit.getIcon();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + kit.name);
                icon.setItemMeta(meta);
            }
            inv.addItem(icon);
        }
        ItemStack ownInv = new ItemStack(Material.CHEST);
        ItemMeta meta = ownInv.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Own Inventory");
            ownInv.setItemMeta(meta);
        }
        inv.addItem(ownInv);
        inDuelMenu.put(challenger.getUniqueId(), true);
        challenger.openInventory(inv);
        KitMenuListener listener = new KitMenuListener(challenger, target, inv);
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    private void openMapMenu(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.AQUA + "Select a Map");
        ItemStack bedrock = new ItemStack(Material.BEDROCK);
        ItemMeta meta = bedrock.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Bedrock Box");
            bedrock.setItemMeta(meta);
        }
        inv.setItem(4, bedrock);
        inDuelMenu.put(challenger.getUniqueId(), true);
        challenger.openInventory(inv);
        MapMenuListener listener = new MapMenuListener(challenger, target, kitName, ownInvContents, ownArmor, inv);
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    private void sendDuelRequest(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName) {
        DuelRequest req = new DuelRequest(challenger, target, kitName, ownInvContents, ownArmor, mapName);
        pendingRequests.put(target.getUniqueId(), req);
        String accept = ChatColor.GREEN + "[ACCEPT]";
        String deny = ChatColor.RED + "[DENY]";
        target.sendMessage(ChatColor.AQUA + challenger.getName() + " has challenged you to a duel! Kit: " + kitName + ", Map: " + mapName);
        target.sendMessage(accept + " " + deny);
        target.sendMessage(ChatColor.GRAY + "Type 'accept' or 'deny' in chat. This request will expire in 30 seconds.");

        DuelRequestListener listener = new DuelRequestListener(target, challenger, req);
        Bukkit.getPluginManager().registerEvents(listener, this);

        BukkitRunnable timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRequests.containsKey(target.getUniqueId())) {
                    pendingRequests.remove(target.getUniqueId());
                    HandlerList.unregisterAll(listener);
                    challenger.sendMessage(ChatColor.RED + "Your duel request to " + target.getName() + " has expired.");
                    target.sendMessage(ChatColor.RED + "Duel request expired.");
                }
                duelRequestTimeouts.remove(target.getUniqueId());
            }
        };
        timeoutTask.runTaskLater(this, 20 * 30);
        duelRequestTimeouts.put(target.getUniqueId(), timeoutTask);

        challenger.sendMessage(ChatColor.YELLOW + "Duel request sent to " + target.getName() + ".");
    }

  
    private int getNextDuelOffset() {
        synchronized (duelSessions) {
            return duelSessions.size() * 200;
        }
    }

  
    private DuelSession getSessionForPlayer(Player p) {
        return activeDuels.get(p.getUniqueId());
    }

   
    private List<DuelSession> getActiveDuelSessions() {
        synchronized (duelSessions) {
            return new ArrayList<>(duelSessions);
        }
    }

    private void startDuel(DuelRequest req) {
        Player p1 = req.challenger;
        Player p2 = req.target;
        if (!p1.isOnline() || !p2.isOnline()) {
            if (p1.isOnline()) p1.sendMessage(ChatColor.RED + "Duel cancelled: one player offline.");
            if (p2.isOnline()) p2.sendMessage(ChatColor.RED + "Duel cancelled: one player offline.");
            pendingRequests.remove(p2.getUniqueId());
            BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
            if (timeout != null) timeout.cancel();
            return;
        }
        if (isInDuel(p1) || isInDuel(p2)) {
            p1.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
            p2.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
            pendingRequests.remove(p2.getUniqueId());
            BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
            if (timeout != null) timeout.cancel();
            return;
        }

        
        int duelOffset = getNextDuelOffset();

        Arena duelArena = arena.offset(duelOffset);

        DuelSession session = new DuelSession(p1, p2, req.kitName, req.ownInvContents, req.ownArmor, req.mapName, duelArena, duelOffset);
        activeDuels.put(p1.getUniqueId(), session);
        activeDuels.put(p2.getUniqueId(), session);
        synchronized (duelSessions) {
            duelSessions.add(session);
        }
        session.start();
        pendingRequests.remove(p2.getUniqueId());
        BukkitRunnable timeout = duelRequestTimeouts.remove(p2.getUniqueId());
        if (timeout != null) timeout.cancel();
    }

    private void endDuel(DuelSession session, Player winner, Player loser, String reason) {
        session.end(winner, loser, reason);
        activeDuels.remove(session.p1.getUniqueId());
        activeDuels.remove(session.p2.getUniqueId());
        synchronized (duelSessions) {
            duelSessions.remove(session);
        }
        if (winner != null) incrementWin(winner.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isInDuel(p)) {
            DuelSession session = activeDuels.get(p.getUniqueId());
            Player other = session.getOther(p);
            endDuel(session, other, p, "disconnect");
        }
        pendingRequests.remove(p.getUniqueId());
        inDuelMenu.remove(p.getUniqueId());
        BukkitRunnable timeout = duelRequestTimeouts.remove(p.getUniqueId());
        if (timeout != null) timeout.cancel();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (isInDuel(p)) {
            e.getDrops().clear();
            e.setDroppedExp(0);

            DuelSession session = activeDuels.get(p.getUniqueId());
            Player winner = session.getOther(p);
            Bukkit.getScheduler().runTaskLater(this, () -> endDuel(session, winner, p, "death"), 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isInDuel(p)) {
            DuelSession session = activeDuels.get(p.getUniqueId());
            if (!session.isInsideArena(p.getLocation())) {
                Location safe = session.getArenaCenter();
                p.teleport(safe);
                p.sendMessage(ChatColor.RED + "You cannot leave the duel arena!");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (inDuelMenu.containsKey(e.getPlayer().getUniqueId())) {
            inDuelMenu.remove(e.getPlayer().getUniqueId());
        }
    }

    public class DuelCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            try {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                Player challenger = (Player) sender;
                if (args.length != 1) {
                    challenger.sendMessage(ChatColor.YELLOW + "Usage: /duel <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    challenger.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                if (target.equals(challenger)) {
                    challenger.sendMessage(ChatColor.RED + "You cannot duel yourself.");
                    return true;
                }
                if (isInDuel(challenger) || isInDuel(target)) {
                    challenger.sendMessage(ChatColor.RED + "You or your target is already in a duel.");
                    return true;
                }
                if (isInRequest(target)) {
                    challenger.sendMessage(ChatColor.RED + "That player already has a pending duel request.");
                    return true;
                }
                openKitMenu(challenger, target);
                return true;
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "An error occurred while processing the command. Please contact an admin.");
                ex.printStackTrace();
                return true;
            }
        }
    }

    public class LeaderboardCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(duelWins.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            sender.sendMessage(ChatColor.AQUA + "=== Duel Leaderboard ===");
            int i = 1;
            for (Map.Entry<UUID, Integer> entry : sorted) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString();
                sender.sendMessage(ChatColor.YELLOW + "" + i + ". " + name + ": " + entry.getValue() + " wins");
                if (++i > 10) break;
            }
            return true;
        }
    }

 
    public class DuelStatsCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            int wins = duelWins.getOrDefault(uuid, 0);
            player.sendMessage(ChatColor.AQUA + "=== Your Duel Stats ===");
            player.sendMessage(ChatColor.YELLOW + "Wins: " + wins);
            return true;
        }
    }

    public class SpectateCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player player = (Player) sender;
            List<DuelSession> sessions = getActiveDuelSessions();
            if (sessions.isEmpty()) {
                player.sendMessage(ChatColor.RED + "There are no active duels to spectate.");
                return true;
            }
            DuelSession session = null;
            if (args.length == 0) {
                session = sessions.get(0);
            } else {
                String name = args[0];
                for (DuelSession s : sessions) {
                    if (s.p1.getName().equalsIgnoreCase(name) || s.p2.getName().equalsIgnoreCase(name)) {
                        session = s;
                        break;
                    }
                }
                if (session == null) {
                    player.sendMessage(ChatColor.RED + "No duel found for player: " + name);
                    return true;
                }
            }
            Location center = session.getArenaCenter();
            player.teleport(center.clone().add(0, 5, 0));
            player.sendMessage(ChatColor.GREEN + "You are now spectating the duel between " + session.p1.getName() + " and " + session.p2.getName() + ".");
            return true;
        }
    }

    public class KitMenuListener implements Listener {
        private final Player challenger;
        private final Player target;
        private final Inventory inv;
        public KitMenuListener(Player challenger, Player target, Inventory inv) {
            this.challenger = challenger;
            this.target = target;
            this.inv = inv;
        }
        @EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (!e.getWhoClicked().equals(challenger)) return;
            if (!e.getInventory().equals(inv)) return;
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (name.equalsIgnoreCase("Own Inventory")) {
                List<ItemStack> invContents = Arrays.asList(challenger.getInventory().getContents());
                List<ItemStack> armor = Arrays.asList(challenger.getInventory().getArmorContents());
                challenger.closeInventory();
                HandlerList.unregisterAll(this);
                openMapMenu(challenger, target, "Own Inventory", invContents, armor);
                return;
            }
            Kit kit = null;
            for (Kit k : kits.values()) {
                if (k.name.equalsIgnoreCase(name)) {
                    kit = k;
                    break;
                }
            }
            if (kit == null) {
                challenger.sendMessage(ChatColor.RED + "Kit not found.");
                challenger.closeInventory();
                HandlerList.unregisterAll(this);
                return;
            }
            challenger.closeInventory();
            HandlerList.unregisterAll(this);
            openMapMenu(challenger, target, kit.name, null, null);
        }
    }

    public class MapMenuListener implements Listener {
        private final Player challenger;
        private final Player target;
        private final String kitName;
        private final List<ItemStack> ownInvContents;
        private final List<ItemStack> ownArmor;
        private final Inventory inv;
        public MapMenuListener(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, Inventory inv) {
            this.challenger = challenger;
            this.target = target;
            this.kitName = kitName;
            this.ownInvContents = ownInvContents;
            this.ownArmor = ownArmor;
            this.inv = inv;
        }
        @EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (!e.getWhoClicked().equals(challenger)) return;
            if (!e.getInventory().equals(inv)) return;
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (name.equalsIgnoreCase("Bedrock Box")) {
                challenger.closeInventory();
                HandlerList.unregisterAll(this);
                sendDuelRequest(challenger, target, kitName, ownInvContents, ownArmor, "Bedrock Box");
            }
        }
    }

    public class DuelRequestListener implements Listener {
        private final Player target;
        private final Player challenger;
        private final DuelRequest req;
        public DuelRequestListener(Player target, Player challenger, DuelRequest req) {
            this.target = target;
            this.challenger = challenger;
            this.req = req;
        }
        @EventHandler
        public void onPlayerChat(AsyncPlayerChatEvent e) {
            if (!e.getPlayer().equals(target)) return;
            String msg = e.getMessage().trim().toLowerCase();
            if (msg.equals("accept")) {
                e.setCancelled(true);
                HandlerList.unregisterAll(this);
                BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                if (timeout != null) timeout.cancel();
                Bukkit.getScheduler().runTask(Duel.this, () -> startDuel(req));
            } else if (msg.equals("deny")) {
                e.setCancelled(true);
                HandlerList.unregisterAll(this);
                target.sendMessage(ChatColor.RED + "Duel request denied.");
                challenger.sendMessage(ChatColor.RED + "Your duel request was denied.");
                pendingRequests.remove(target.getUniqueId());
                BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                if (timeout != null) timeout.cancel();
            }
        }
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent e) {
            if (e.getPlayer().equals(target) || e.getPlayer().equals(challenger)) {
                HandlerList.unregisterAll(this);
                pendingRequests.remove(target.getUniqueId());
                BukkitRunnable timeout = duelRequestTimeouts.remove(target.getUniqueId());
                if (timeout != null) timeout.cancel();
            }
        }
    }

    public static class Kit {
        public final String name;
        public final String iconName;
        public final List<String> itemList;
        public Kit(String name, String iconName, List<String> itemList) {
            this.name = name;
            this.iconName = iconName;
            this.itemList = itemList;
        }
        public ItemStack getIcon() {
            Material mat;
            try {
                mat = Material.valueOf(iconName.toUpperCase());
            } catch (Exception e) {
                mat = Material.STONE;
            }
            return new ItemStack(mat);
        }
        public List<ItemStack> getItems() {
            List<ItemStack> items = new ArrayList<>();
            for (String s : itemList) {
                String[] split = s.split("\\*");
                String matName = split[0];
                int amt = 1;
                if (split.length > 1) {
                    try { amt = Integer.parseInt(split[1]); } catch (Exception ignored) {}
                }
                Material mat;
                try {
                    mat = Material.valueOf(matName.toUpperCase());
                } catch (Exception e) {
                    mat = Material.STONE;
                }
                items.add(new ItemStack(mat, amt));
            }
            return items;
        }

        public ItemStack[] getArmorContents() {
            ItemStack[] armor = new ItemStack[4];
            for (String s : itemList) {
                String[] split = s.split("\\*");
                String matName = split[0].toUpperCase();
                Material mat;
                try {
                    mat = Material.valueOf(matName);
                } catch (Exception e) {
                    continue;
                }
                ItemStack item = new ItemStack(mat, 1);
                if (matName.endsWith("_HELMET")) {
                    armor[3] = item;
                } else if (matName.endsWith("_CHESTPLATE")) {
                    armor[2] = item;
                } else if (matName.endsWith("_LEGGINGS")) {
                    armor[1] = item;
                } else if (matName.endsWith("_BOOTS")) {
                    armor[0] = item;
                }
            }
            return armor;
        }
    }

    public static class Arena {
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Arena(String world, List<Integer> c1, List<Integer> c2) {
            this.world = world;
            this.minX = Math.min(c1.get(0), c2.get(0));
            this.minY = Math.min(c1.get(1), c2.get(1));
            this.minZ = Math.min(c1.get(2), c2.get(2));
            this.maxX = Math.max(c1.get(0), c2.get(0));
            this.maxY = Math.max(c1.get(1), c2.get(1));
            this.maxZ = Math.max(c1.get(2), c2.get(2));
        }

        public Arena offset(int offset) {
            return new Arena(
                    world,
                    Arrays.asList(minX + offset, minY, minZ + offset),
                    Arrays.asList(maxX + offset, maxY, maxZ + offset)
            );
        }

        public Location getCenter() {
            World w = Bukkit.getWorld(world);
            return new Location(w, (minX + maxX) / 2.0 + 0.5, minY + 1, (minZ + maxZ) / 2.0 + 0.5);
        }
        public Location getSpawn1() {
            World w = Bukkit.getWorld(world);
            return new Location(w, minX + 2.5, minY + 1, minZ + 2.5);
        }
        public Location getSpawn2() {
            World w = Bukkit.getWorld(world);
            return new Location(w, maxX - 2.5, minY + 1, maxZ - 2.5);
        }
        public boolean isInside(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equals(world)) return false;
            return loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                    loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                    loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }
        public void buildBox() {
            World w = Bukkit.getWorld(world);
            if (w == null) return;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean wall = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                        if (wall) {
                            w.getBlockAt(x, y, z).setType(Material.BEDROCK);
                        } else {
                            w.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }
            for (int x = minX + 1; x < maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    for (int z = minZ + 1; z < maxZ; z++) {
                        if ((x + y + z) % 7 == 0) {
                            w.getBlockAt(x, y, z).setType(Material.LIGHT);
                        }
                    }
                }
            }
        }
    }

    public static class DuelRequest {
        public final Player challenger;
        public final Player target;
        public final String kitName;
        public final List<ItemStack> ownInvContents;
        public final List<ItemStack> ownArmor;
        public final String mapName;
        public DuelRequest(Player challenger, Player target, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName) {
            this.challenger = challenger;
            this.target = target;
            this.kitName = kitName;
            this.ownInvContents = ownInvContents;
            this.ownArmor = ownArmor;
            this.mapName = mapName;
        }
    }

    public class DuelSession {
        public final Player p1, p2;
        public final String kitName;
        public final List<ItemStack> ownInvContents;
        public final List<ItemStack> ownArmor;
        public final String mapName;
        public final Arena arena;
        private final ItemStack[] p1Inv, p2Inv, p1Armor, p2Armor;
        private final Location p1Loc, p2Loc;
        private boolean started = false;
        private final int duelOffset;

        public DuelSession(Player p1, Player p2, String kitName, List<ItemStack> ownInvContents, List<ItemStack> ownArmor, String mapName, Arena arena, int duelOffset) {
            this.p1 = p1;
            this.p2 = p2;
            this.kitName = kitName;
            this.ownInvContents = ownInvContents;
            this.ownArmor = ownArmor;
            this.mapName = mapName;
            this.arena = arena;
            this.duelOffset = duelOffset;
            this.p1Inv = p1.getInventory().getContents();
            this.p2Inv = p2.getInventory().getContents();
            this.p1Armor = p1.getInventory().getArmorContents();
            this.p2Armor = p2.getInventory().getArmorContents();
            this.p1Loc = p1.getLocation();
            this.p2Loc = p2.getLocation();
        }
        public void start() {
            arena.buildBox();
            p1.sendMessage(ChatColor.AQUA + "Duel starting in 3 seconds...");
            p2.sendMessage(ChatColor.AQUA + "Duel starting in 3 seconds...");
         
            p1.sendMessage(ChatColor.LIGHT_PURPLE + "Type " + ChatColor.YELLOW + "/spectate " + p1.getName() + ChatColor.LIGHT_PURPLE + " to spectate this duel!");
            p2.sendMessage(ChatColor.LIGHT_PURPLE + "Type " + ChatColor.YELLOW + "/spectate " + p1.getName() + ChatColor.LIGHT_PURPLE + " to spectate this duel!");
            new BukkitRunnable() {
                int count = 3;
                @Override
                public void run() {
                    if (count > 0) {
                        p1.sendTitle(ChatColor.RED + "" + count, "", 0, 20, 0);
                        p2.sendTitle(ChatColor.RED + "" + count, "", 0, 20, 0);
                        p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                        count--;
                    } else {
                        p1.sendTitle(ChatColor.GREEN + "FIGHT!", "", 0, 20, 0);
                        p2.sendTitle(ChatColor.GREEN + "FIGHT!", "", 0, 20, 0);
                        teleportPlayers();
                        giveKits();
                        started = true;
                        this.cancel();
                    }
                }
            }.runTaskTimer(Duel.this, 0L, 20L);
        }
        private void teleportPlayers() {
            p1.teleport(arena.getSpawn1());
            p2.teleport(arena.getSpawn2());
        }
        private void giveKits() {
            p1.getInventory().clear();
            p2.getInventory().clear();
            p1.getInventory().setArmorContents(null);
            p2.getInventory().setArmorContents(null);
            if (kitName.equalsIgnoreCase("Own Inventory")) {
                if (ownInvContents != null && ownArmor != null) {
                    p1.getInventory().setContents(ownInvContents.toArray(new ItemStack[0]));
                    p2.getInventory().setContents(ownInvContents.toArray(new ItemStack[0]));
                    p1.getInventory().setArmorContents(ownArmor.toArray(new ItemStack[0]));
                    p2.getInventory().setArmorContents(ownArmor.toArray(new ItemStack[0]));
                }
            } else {
                Kit kit = kits.get(kitName.toLowerCase());
                if (kit != null) {
                    List<ItemStack> items = kit.getItems();
                    List<ItemStack> nonArmor = new ArrayList<>();
                    for (ItemStack item : items) {
                        if (item == null) continue;
                        Material mat = item.getType();
                        String matName = mat.name();
                        if (matName.endsWith("_HELMET") || matName.endsWith("_CHESTPLATE") || matName.endsWith("_LEGGINGS") || matName.endsWith("_BOOTS")) {
                            continue;
                        }
                        nonArmor.add(item.clone());
                    }
                    for (ItemStack item : nonArmor) {
                        p1.getInventory().addItem(item.clone());
                        p2.getInventory().addItem(item.clone());
                    }
                    ItemStack[] armor = kit.getArmorContents();
                    p1.getInventory().setArmorContents(armor);
                    p2.getInventory().setArmorContents(armor);
                }
            }
        }
        public boolean isInsideArena(Location loc) {
            return arena.isInside(loc);
        }
        public Location getArenaCenter() {
            return arena.getCenter();
        }
        public Player getOther(Player p) {
            return p.equals(p1) ? p2 : p1;
        }
        public void end(Player winner, Player loser, String reason) {
            started = false;
            restorePlayers();
            if (winner != null && loser != null) {
                Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " has won a duel against " + loser.getName() + "!");
            }
            p1.teleport(p1Loc);
            p2.teleport(p2Loc);

            World w = Bukkit.getWorld(arena.world);
            if (w != null) {
                for (Item item : w.getEntitiesByClass(Item.class)) {
                    Location loc = item.getLocation();
                    if (arena.isInside(loc)) {
                        item.remove();
                    }
                }
            }
        }
        private void restorePlayers() {
            p1.getInventory().setContents(p1Inv);
            p2.getInventory().setContents(p2Inv);
            p1.getInventory().setArmorContents(p1Armor);
            p2.getInventory().setArmorContents(p2Armor);
        }
    }
}
