// a plugin that links 2 players, they share their health and potions
package me.tibo.soulLink;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SoulLink extends JavaPlugin implements Listener, TabExecutor {

    private final HashMap<UUID, UUID> soulLinks = new HashMap<>();
    private final HashMap<UUID, Long> lastDamageTime = new HashMap<>();
    private final HashMap<UUID, UUID> previousLinks = new HashMap<>();
    private static final long COOLDOWN_TIME = 100L;

    @Override
    public void onEnable() {
        getLogger().info("SoulLink plugin has been enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("soullink").setExecutor(this);
        getCommand("soulunlink").setExecutor(this);
        getCommand("soullink").setTabCompleter(this);
        getCommand("soulunlink").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SoulLink plugin disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You don't have permission to use this command!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("soullink")) {
            if (args.length != 2) {
                sender.sendMessage("Usage: /soullink <player1> <player2>");
                return true;
            }

            Player player1 = Bukkit.getPlayer(args[0]);
            Player player2 = Bukkit.getPlayer(args[1]);

            if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
                sender.sendMessage("One or both players aren't online");
                return true;
            }

            if (player1.getUniqueId().equals(player2.getUniqueId())) {
                sender.sendMessage("You can't link with yourself...");
                return true;
            }

            soulLinks.put(player1.getUniqueId(), player2.getUniqueId());
            soulLinks.put(player2.getUniqueId(), player1.getUniqueId());

            player1.sendMessage("You are now soul-linked with " + player2.getName() + "!");
            player2.sendMessage("You are now soul-linked with " + player1.getName() + "!");

            updateMaxHealth(player1);
            updateMaxHealth(player2);
            return true;
        }

        if (command.getName().equalsIgnoreCase("soulunlink")) {
            if (args.length != 2) {
                sender.sendMessage("Usage: /soulunlink <player1> <player2>");
                return true;
            }

            Player player1 = Bukkit.getPlayer(args[0]);
            Player player2 = Bukkit.getPlayer(args[1]);

            if (player1 == null || player2 == null) {
                sender.sendMessage("One or both players are not online.");
                return true;
            }

            if (soulLinks.get(player1.getUniqueId()) != null && soulLinks.get(player1.getUniqueId()).equals(player2.getUniqueId())) {
                soulLinks.remove(player1.getUniqueId());
                soulLinks.remove(player2.getUniqueId());

                player1.sendMessage("You have been unlinked from " + player2.getName() + "!");
                player2.sendMessage("You have been unlinked from " + player1.getName() + "!");

                updateMaxHealth(player1);
                updateMaxHealth(player2);
            } else {
                sender.sendMessage("These players are not linked!");
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID linkedUUID = soulLinks.get(player.getUniqueId());
        if (linkedUUID == null) return;

        Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
        if (linkedPlayer == null || !linkedPlayer.isOnline()) {
            soulLinks.remove(player.getUniqueId());
            player.sendMessage("Your linked player is no longer online.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        long lastDamage = lastDamageTime.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastDamage < COOLDOWN_TIME) {
            return;
        }

        lastDamageTime.put(player.getUniqueId(), currentTime);
        lastDamageTime.put(linkedPlayer.getUniqueId(), currentTime);

        linkedPlayer.damage(event.getDamage());
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID linkedUUID = soulLinks.get(player.getUniqueId());
        if (linkedUUID == null) return;

        Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
        if (linkedPlayer == null || !linkedPlayer.isOnline()) {
            soulLinks.remove(player.getUniqueId());
            player.sendMessage("Your linked player is no longer online.");
            return;
        }

        linkedPlayer.setHealth(Math.min(linkedPlayer.getHealth() + event.getAmount(), linkedPlayer.getMaxHealth()));
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        UUID linkedUUID = soulLinks.get(player.getUniqueId());
        if (linkedUUID == null) return;

        Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
        if (linkedPlayer == null || !linkedPlayer.isOnline()) {
            soulLinks.remove(player.getUniqueId());
            player.sendMessage("Your linked player is no longer online.");
            return;
        }

        if (event.getNewEffect() != null) {
            linkedPlayer.addPotionEffect(event.getNewEffect());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (previousLinks.containsKey(player.getUniqueId())) {
            UUID linkedUUID = previousLinks.get(player.getUniqueId());
            Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
            if (linkedPlayer != null && linkedPlayer.isOnline()) {
                soulLinks.put(player.getUniqueId(), linkedUUID);
                soulLinks.put(linkedUUID, player.getUniqueId());
                player.sendMessage("You have been re-linked to " + linkedPlayer.getName() + "!");
                updateMaxHealth(player);
                updateMaxHealth(linkedPlayer);
            }
        }

        updateMaxHealth(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID linkedUUID = soulLinks.remove(player.getUniqueId());
        if (linkedUUID != null) {
            Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
            if (linkedPlayer != null && linkedPlayer.isOnline()) {
                linkedPlayer.sendMessage(player.getName() + " has left, both you and " + player.getName() + " now have 10 hearts.");
                updateMaxHealth(linkedPlayer);
            }
            previousLinks.put(player.getUniqueId(), linkedUUID);
        }
    }

    private void updateMaxHealth(Player player) {
        UUID linkedUUID = soulLinks.get(player.getUniqueId());
        double newMaxHealth = 10.0;

        if (linkedUUID != null) {
            Player linkedPlayer = Bukkit.getPlayer(linkedUUID);
            if (linkedPlayer == null || !linkedPlayer.isOnline()) {
                soulLinks.remove(player.getUniqueId());
                player.sendMessage("Your soul link has been broken because the linked player is no longer online.");
            } else {
                newMaxHealth = 20.0;
            }
        }

        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMaxHealth);
            player.setHealth(Math.min(player.getHealth(), newMaxHealth));
        }
    }
}
