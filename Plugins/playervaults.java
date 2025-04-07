package me.tibo.playervaults;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class PersonalVault extends JavaPlugin {
    private FileConfiguration config;
    private Map<UUID, Map<Integer, Inventory>> playerVaults;
    private Map<UUID, Integer> vaultLimits;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        playerVaults = new HashMap<>();
        vaultLimits = new HashMap<>();

        getCommand("vault").setExecutor(new VaultCommand());
        getServer().getPluginManager().registerEvents(new VaultListener(), this);

        loadVaultLimits();
    }

    private void loadVaultLimits() {
        if (config.contains("vault-limits")) {
            for (String uuid : config.getConfigurationSection("vault-limits").getKeys(false)) {
                vaultLimits.put(UUID.fromString(uuid), config.getInt("vault-limits." + uuid));
            }
        }
    }

    public void saveVaultLimits() {
        for (Map.Entry<UUID, Integer> entry : vaultLimits.entrySet()) {
            config.set("vault-limits." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    public FileConfiguration getVaultConfig() {
        return config;
    }

    public Map<UUID, Map<Integer, Inventory>> getPlayerVaults() {
        return playerVaults;
    }

    public int getVaultLimit(UUID uuid) {
        return vaultLimits.getOrDefault(uuid, 1);
    }

    public void setVaultLimit(UUID uuid, int limit) {
        vaultLimits.put(uuid, limit);
        saveVaultLimits();
    }
}

class VaultCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("personalvault.use")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        PersonalVault plugin = PersonalVault.getPlugin(PersonalVault.class);

        if (args.length == 0) {
            int limit = plugin.getVaultLimit(player.getUniqueId());
            player.sendMessage("§aYou have access to " + limit + " vault(s)!");
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("upgrade") && player.hasPermission("personalvault.upgrade")) {
                int currentLimit = plugin.getVaultLimit(player.getUniqueId());
                if (currentLimit >= 5) {
                    player.sendMessage("§cYou already have the maximum number of vaults!");
                    return true;
                }
                plugin.setVaultLimit(player.getUniqueId(), currentLimit + 1);
                player.sendMessage("§aVault limit increased to " + (currentLimit + 1) + "!");
                return true;
            }

            int vaultNumber;
            try {
                vaultNumber = Integer.parseInt(args[0]);
                if (vaultNumber < 1 || vaultNumber > plugin.getVaultLimit(player.getUniqueId())) {
                    player.sendMessage("§cInvalid vault number!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid vault number!");
                return true;
            }

            Map<UUID, Map<Integer, Inventory>> playerVaults = plugin.getPlayerVaults();
            UUID playerUUID = player.getUniqueId();

            if (!playerVaults.containsKey(playerUUID)) {
                playerVaults.put(playerUUID, new HashMap<>());
            }

            Map<Integer, Inventory> vaults = playerVaults.get(playerUUID);
            if (!vaults.containsKey(vaultNumber)) {
                Inventory vault = Bukkit.createInventory(null, 54, "§6Vault " + vaultNumber);
                ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta borderMeta = border.getItemMeta();
                borderMeta.setDisplayName(" ");
                border.setItemMeta(borderMeta);

                for (int i = 0; i < 9; i++) {
                    vault.setItem(i, border);
                    vault.setItem(45 + i, border);
                }
                for (int i = 0; i < 6; i++) {
                    vault.setItem(i * 9, border);
                    vault.setItem(i * 9 + 8, border);
                }

                vaults.put(vaultNumber, vault);
            }

            player.openInventory(vaults.get(vaultNumber));
            return true;
        }

        player.sendMessage("§cUsage: /vault [number] or /vault upgrade");
        return true;
    }
}

class VaultListener implements Listener {
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String title = event.getInventory().getType().name();

        if (title.startsWith("§6Vault ")) {
            int vaultNumber = Integer.parseInt(title.split(" ")[1]);
            PersonalVault plugin = PersonalVault.getPlugin(PersonalVault.class);
            Map<UUID, Map<Integer, Inventory>> playerVaults = plugin.getPlayerVaults();

            playerVaults.get(player.getUniqueId()).put(vaultNumber, event.getInventory());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("§6Vault ")) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith("§6Vault ")) {
            for (int slot : event.getRawSlots()) {
                if (event.getView().getItem(slot) != null &&
                        event.getView().getItem(slot).getType() == Material.BLACK_STAINED_GLASS_PANE) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
