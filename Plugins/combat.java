//combat plugin that punishes player when he logs of in combat and prevents commands in combat
package me.Tibo.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Combat extends JavaPlugin implements Listener {

    private final Map<UUID, Long> combatTimes = new HashMap<>();
    private final Map<UUID, Boolean> playerLoggedOutInCombat = new HashMap<>();
    private final long combatTimeout = 10000;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getLogger().info("Combat plugin enabled successfully");
    }

    @Override
    public void onDisable() {
        combatTimes.clear();
        playerLoggedOutInCombat.clear();
        Bukkit.getLogger().info("Combat plugin disabled successfully");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        long currentTime = System.currentTimeMillis();

        combatTimes.put(victim.getUniqueId(), currentTime);
        combatTimes.put(attacker.getUniqueId(), currentTime);
        playerLoggedOutInCombat.put(victim.getUniqueId(), false);
        playerLoggedOutInCombat.put(attacker.getUniqueId(), false);

        notifyCombatStatus(victim);
        notifyCombatStatus(attacker);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!isInCombat(playerId)) {
            return;
        }

        dropLoot(player);
        playerLoggedOutInCombat.put(playerId, true);
        player.damage(20);
        Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " logged out during combat!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (playerLoggedOutInCombat.getOrDefault(playerId, false)) {
            player.getInventory().clear();
            player.setHealth(0);
            player.sendMessage(ChatColor.RED + "You were killed for logging out during combat!");
            playerLoggedOutInCombat.put(playerId, false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        combatTimes.remove(playerId);
        playerLoggedOutInCombat.put(playerId, false);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!isInCombat(playerId)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot use commands while in combat! Time remaining: " + 
            getRemainingCombatTime(playerId) / 1000 + " seconds");
    }

    private void dropLoot(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        player.getInventory().clear();
    }

    private boolean isInCombat(UUID playerId) {
        if (!combatTimes.containsKey(playerId)) {
            return false;
        }
        return System.currentTimeMillis() - combatTimes.get(playerId) < combatTimeout;
    }

    private long getRemainingCombatTime(UUID playerId) {
        if (!combatTimes.containsKey(playerId)) {
            return 0;
        }
        return Math.max(0, combatTimeout - (System.currentTimeMillis() - combatTimes.get(playerId)));
    }

    private void notifyCombatStatus(Player player) {
        player.sendMessage(ChatColor.RED + "You are now in combat! Do not log out for " + (combatTimeout / 1000) + " seconds!");
    }
}
