package me.tibo.edititem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class Edititem extends JavaPlugin implements Listener {
    private File configFile;
    private FileConfiguration config;
    private Inventory editMenu;
    private ItemStack currentItem;
    private Map<UUID, ItemStack> editingPlayers;
    private Map<UUID, String> playerStates;
    private Map<UUID, Integer> enchantLevels;
    private Map<UUID, List<String>> itemLore;
    private Map<UUID, Map<Attribute, AttributeModifier>> attributeModifiers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        configFile = new File(getDataFolder(), "items.yml");
        if (!configFile.exists()) {
            saveResource("items.yml", false);
        }
        editingPlayers = new HashMap<>();
        playerStates = new HashMap<>();
        enchantLevels = new HashMap<>();
        itemLore = new HashMap<>();
        attributeModifiers = new HashMap<>();
        createEditMenu();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EditItem plugin has been enabled!");
    }

    private void createEditMenu() {
        editMenu = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Item Editor");
        ItemStack nameItem = createMenuItem(Material.NAME_TAG, "Change Item Name", "Click to edit the item's name");
        ItemStack enchantItem = createMenuItem(Material.ENCHANTED_BOOK, "Edit Enchantments", "Click to modify enchantments");
        ItemStack loreItem = createMenuItem(Material.BOOK, "Edit Lore", "Click to edit item lore");
        ItemStack attributesItem = createMenuItem(Material.DIAMOND, "Edit Attributes", "Click to modify attributes");
        ItemStack unbreakableItem = createMenuItem(Material.ANVIL, "Toggle Unbreakable", "Click to toggle unbreakable");
        ItemStack hideFlagsItem = createMenuItem(Material.BARRIER, "Hide Item Flags", "Click to toggle item flags");
        ItemStack saveItem = createMenuItem(Material.EMERALD, "Save Changes", "Click to save all changes");
        ItemStack cancelItem = createMenuItem(Material.REDSTONE, "Cancel Editing", "Click to cancel editing");

        editMenu.setItem(10, nameItem);
        editMenu.setItem(12, enchantItem);
        editMenu.setItem(14, loreItem);
        editMenu.setItem(16, attributesItem);
        editMenu.setItem(28, unbreakableItem);
        editMenu.setItem(30, hideFlagsItem);
        editMenu.setItem(49, saveItem);
        editMenu.setItem(51, cancelItem);
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> itemLore = new ArrayList<>();
        for (String line : lore) {
            itemLore.add(ChatColor.GRAY + line);
        }
        meta.setLore(itemLore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "Item Editor")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot >= 0 && slot < 54) {
            switch (slot) {
                case 10:
                    playerStates.put(player.getUniqueId(), "naming");
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Enter the new name in chat:");
                    break;
                case 12:
                    openEnchantMenu(player);
                    break;
                case 14:
                    playerStates.put(player.getUniqueId(), "lore");
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Enter the new lore line in chat:");
                    break;
                case 16:
                    openAttributeMenu(player);
                    break;
                case 28:
                    toggleUnbreakable(player);
                    break;
                case 30:
                    toggleItemFlags(player);
                    break;
                case 49:
                    saveChanges(player);
                    break;
                case 51:
                    cancelEditing(player);
                    break;
            }
        }
    }

    private void openEnchantMenu(Player player) {
        Inventory enchantMenu = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Enchantment Editor");
        for (Enchantment enchant : Enchantment.values()) {
            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + enchant.getKey().getKey());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Current Level: " + getEnchantLevel(player, enchant));
            lore.add(ChatColor.YELLOW + "Click to change level");
            meta.setLore(lore);
            item.setItemMeta(meta);
            enchantMenu.addItem(item);
        }
        player.openInventory(enchantMenu);
    }

    private void openAttributeMenu(Player player) {
        Inventory attributeMenu = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Attribute Editor");
        for (Attribute attribute : Attribute.values()) {
            ItemStack item = new ItemStack(Material.DIAMOND);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + attribute.name());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to modify");
            meta.setLore(lore);
            item.setItemMeta(meta);
            attributeMenu.addItem(item);
        }
        player.openInventory(attributeMenu);
    }

    private void toggleUnbreakable(Player player) {
        ItemStack item = editingPlayers.get(player.getUniqueId());
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setUnbreakable(!meta.isUnbreakable());
            item.setItemMeta(meta);
            player.sendMessage(ChatColor.GREEN + "Unbreakable " + (meta.isUnbreakable() ? "enabled" : "disabled"));
        }
    }

    private void toggleItemFlags(Player player) {
        ItemStack item = editingPlayers.get(player.getUniqueId());
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
                meta.removeItemFlags(ItemFlag.values());
            } else {
                meta.addItemFlags(ItemFlag.values());
            }
            item.setItemMeta(meta);
            player.sendMessage(ChatColor.GREEN + "Item flags toggled");
        }
    }

    private void saveChanges(Player player) {
        ItemStack item = editingPlayers.get(player.getUniqueId());
        if (item != null) {
            player.getInventory().setItemInMainHand(item);
            player.sendMessage(ChatColor.GREEN + "Changes saved successfully!");
            cleanupPlayer(player);
        }
    }

    private void cancelEditing(Player player) {
        cleanupPlayer(player);
        player.sendMessage(ChatColor.YELLOW + "Editing cancelled");
    }

    private void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        editingPlayers.remove(uuid);
        playerStates.remove(uuid);
        enchantLevels.remove(uuid);
        itemLore.remove(uuid);
        attributeModifiers.remove(uuid);
    }

    private int getEnchantLevel(Player player, Enchantment enchant) {
        ItemStack item = editingPlayers.get(player.getUniqueId());
        return item != null ? item.getEnchantmentLevel(enchant) : 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        








