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

import java.util.HashMap;
import java.util.Map;

public final class Combat extends JavaPlugin implements Listener {

    private final Map<Player, Long> combatTimes = new HashMap<>();
    private final Map<Player, Boolean> playerLoggedOutInCombat = new HashMap<>();
    private final long combatTimeout = 10000;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        System.out.println("plugin enabled");
    }

    @Override
    public void onDisable() {
        System.out.println("plugin disabled");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player player = (Player) event.getEntity();
            combatTimes.put(player, System.currentTimeMillis());
            playerLoggedOutInCombat.put(player, false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (combatTimes.containsKey(player)) {
            long lastCombatTime = combatTimes.get(player);
            long timeElapsed = System.currentTimeMillis() - lastCombatTime;

            if (timeElapsed < combatTimeout) {
                dropLoot(player);
                playerLoggedOutInCombat.put(player, true);
                player.damage(5); // Optional damage penalty for logging out during combat
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (playerLoggedOutInCombat.getOrDefault(player, false)) {
            player.getInventory().clear();
            player.setHealth(0);
            player.sendMessage("You logged out while in combat!");
            playerLoggedOutInCombat.put(player, false); // Reset combat logout flag
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (playerLoggedOutInCombat.getOrDefault(player, false)) {
            playerLoggedOutInCombat.put(player, false);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (combatTimes.containsKey(player)) {
            long lastCombatTime = combatTimes.get(player);
            long timeElapsed = System.currentTimeMillis() - lastCombatTime;

            if (timeElapsed < combatTimeout) {
                event.setCancelled(true);
                player.sendMessage("You cannot use commands while in combat!");
            }
        }
    }

    private void dropLoot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (player.getInventory().getItem(i) != null) {
                player.getWorld().dropItem(player.getLocation(), player.getInventory().getItem(i));
            }
        }
        player.getInventory().clear();
    }
}
