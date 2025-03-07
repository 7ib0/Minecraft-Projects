// includes: /pay, /balance, /ah, /ah sell
package me.tibo.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class Economy extends JavaPlugin implements Listener {
    private final HashMap<UUID, List<AuctionItem>> auctionItems = new HashMap<>();
    private final List<AuctionItem> activeListings = new ArrayList<>();
    private final HashMap<UUID, Double> playerBalances = new HashMap<>();
    private double minPrice;
    private double maxPrice;
    private int maxAuctionsPerPlayer;
    private String guiTitle;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        loadBalances();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveBalances();
    }

    private void loadConfig() {
        config = getConfig();
        minPrice = config.getDouble("min-price", 1.0);
        maxPrice = config.getDouble("max-price", 1000000.0);
        maxAuctionsPerPlayer = config.getInt("max-auctions-per-player", 5);
        guiTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("gui-title", "&5✦ Auction House ✦"));

        config.addDefault("min-price", 1.0);
        config.addDefault("max-price", 1000000.0);
        config.addDefault("max-auctions-per-player", 5);
        config.addDefault("gui-title", "&5✦ Auction House ✦");
        config.options().copyDefaults(true);
        saveConfig();
    }

    private void loadBalances() {
        if (config.contains("balances")) {
            for (String uuidString : config.getConfigurationSection("balances").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                double balance = config.getDouble("balances." + uuidString);
                playerBalances.put(uuid, balance);
            }
        }
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Double> entry : playerBalances.entrySet()) {
            config.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    private double getBalance(UUID playerId) {
        return playerBalances.getOrDefault(playerId, 0.0);
    }

    private void setBalance(UUID playerId, double amount) {
        playerBalances.put(playerId, amount);
        saveBalances();
    }

    private boolean withdrawMoney(UUID playerId, double amount) {
        double balance = getBalance(playerId);
        if (balance < amount) return false;
        setBalance(playerId, balance - amount);
        return true;
    }

    private void depositMoney(UUID playerId, double amount) {
        setBalance(playerId, getBalance(playerId) + amount);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getCurrentItem() == null) return;

        AuctionItem clickedItem = activeListings.stream()
                .filter(item -> !item.isSold())
                .filter(item -> item.getItem().isSimilar(event.getCurrentItem()))
                .findFirst()
                .orElse(null);

        if (clickedItem == null) return;

        if (clickedItem.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot buy your own items!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (getBalance(player.getUniqueId()) < clickedItem.getPrice()) {
            player.sendMessage(ChatColor.RED + "You cannot afford this item!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        handlePurchase(player, clickedItem);
    }

    private void handlePurchase(Player buyer, AuctionItem item) {
        withdrawMoney(buyer.getUniqueId(), item.getPrice());
        depositMoney(item.getOwner(), item.getPrice());

        if (buyer.getInventory().firstEmpty() == -1) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), item.getItem());
        } else {
            buyer.getInventory().addItem(item.getItem());
        }

        item.setSold(true);
        Player seller = Bukkit.getPlayer(item.getOwner());
        if (seller != null) {
            seller.sendMessage(ChatColor.GREEN + buyer.getName() + " purchased your " +
                    item.getItem().getType().name().toLowerCase() + " for " + item.getPrice() + " coins!");
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        buyer.sendMessage(ChatColor.GREEN + "Successfully purchased item for " + item.getPrice() + " coins!");
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        refreshAllOpenGUIs();
    }

    private void refreshAllOpenGUIs() {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getOpenInventory().getTitle().equals(guiTitle))
                .forEach(this::openAuctionHouseGUI);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("balance")) {
            player.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.YELLOW +
                    getBalance(player.getUniqueId()) + " coins");
            return true;
        }

        if (command.getName().equalsIgnoreCase("pay")) {
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
                return true;
            }
            handlePayCommand(player, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("ah")) {
            if (args.length == 0) {
                openAuctionHouseGUI(player);
                return true;
            }

            if (args[0].equalsIgnoreCase("sell")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ah sell <price>");
                    return true;
                }
                handleSellCommand(player, args);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload") && player.hasPermission("auctionhouse.reload")) {
                reloadConfig();
                loadConfig();
                player.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                return true;
            }

            sendHelpMessage(player);
            return true;
        }

        return false;
    }

    private void handlePayCommand(Player sender, String[] args) {
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive!");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount!");
            return;
        }

        if (!withdrawMoney(sender.getUniqueId(), amount)) {
            sender.sendMessage(ChatColor.RED + "You don't have enough money!");
            return;
        }

        depositMoney(target.getUniqueId(), amount);
        sender.sendMessage(ChatColor.GREEN + "You sent " + amount + " coins to " + target.getName());
        target.sendMessage(ChatColor.GREEN + "You received " + amount + " coins from " + sender.getName());
    }

    private void openAuctionHouseGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(45 + i, border);
        }

        activeListings.stream()
                .filter(item -> !item.isSold())
                .forEach(item -> {
                    ItemStack displayItem = item.getItem().clone();
                    ItemMeta meta = displayItem.getItemMeta();
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.GOLD + "» Price: " + ChatColor.YELLOW + item.getPrice() + " coins");
                    lore.add(ChatColor.GOLD + "» Seller: " + ChatColor.YELLOW +
                            Bukkit.getOfflinePlayer(item.getOwner()).getName());
                    lore.add("");
                    lore.add(ChatColor.GREEN + "Click to purchase!");
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    gui.addItem(displayItem);
                });

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    private void handleSellCommand(Player player, String[] args) {
        if (getPlayerAuctionCount(player) >= maxAuctionsPerPlayer) {
            player.sendMessage(ChatColor.RED + "You can only have " + maxAuctionsPerPlayer + " active listings!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
            if (price < minPrice || price > maxPrice) {
                player.sendMessage(ChatColor.RED + "Price must be between " + minPrice + " and " + maxPrice);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid price amount.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must hold an item to sell it.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        AuctionItem auctionItem = new AuctionItem(player.getUniqueId(), item.clone(), price);
        activeListings.add(auctionItem);
        auctionItems.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(auctionItem);

        player.getInventory().setItemInMainHand(null);
        player.sendMessage(ChatColor.GREEN + "Item listed for " + price + " coins!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        refreshAllOpenGUIs();
    }

    private int getPlayerAuctionCount(Player player) {
        return (int) activeListings.stream()
                .filter(auction -> auction.getOwner().equals(player.getUniqueId()) && !auction.isSold())
                .count();
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "✦ Auction House Commands ✦");
        player.sendMessage(ChatColor.GRAY + "➤ " + ChatColor.YELLOW + "/ah " +
                ChatColor.GRAY + "- Open auction house");
        player.sendMessage(ChatColor.GRAY + "➤ " + ChatColor.YELLOW + "/ah sell <price> " +
                ChatColor.GRAY + "- Sell held item");
        player.sendMessage(ChatColor.GRAY + "➤ " + ChatColor.YELLOW + "/balance " +
                ChatColor.GRAY + "- Check your balance");
        player.sendMessage(ChatColor.GRAY + "➤ " + ChatColor.YELLOW + "/pay <player> <amount> " +
                ChatColor.GRAY + "- Send money to player");
        if (player.hasPermission("auctionhouse.reload")) {
            player.sendMessage(ChatColor.GRAY + "➤ " + ChatColor.YELLOW + "/ah reload " +
                    ChatColor.GRAY + "- Reload configuration");
        }
        player.sendMessage("");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!playerBalances.containsKey(player.getUniqueId()) && !player.hasPlayedBefore()) {
            playerBalances.put(player.getUniqueId(), 1000.0);
            player.sendMessage(ChatColor.GREEN + "Welcome! You received " + ChatColor.GOLD + "1000 coins" +
                    ChatColor.GREEN + " as a starting balance!");
            saveBalances();
        }
    }
}

class AuctionItem {
    private final UUID owner;
    private final ItemStack item;
    private final double price;
    private boolean sold;
    private final long timestamp;

    public AuctionItem(UUID owner, ItemStack item, double price) {
        this.owner = owner;
        this.item = item;
        this.price = price;
        this.sold = false;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getOwner() { return owner; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public boolean isSold() { return sold; }
    public void setSold(boolean sold) { this.sold = sold; }
    public long getTimestamp() { return timestamp; }
}
