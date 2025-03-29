// simple set home plugin
package me.Tibo.sethome;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

public final class Sethome extends JavaPlugin {

    private final HashMap<UUID, Location> homes = new HashMap<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final int cooldownTime = 30;
    private final String prefix = ChatColor.GOLD + "[Home] " + ChatColor.GRAY;

    @Override
    public void onEnable() {
        getCommand("sethome").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + "Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            Location oldHome = homes.put(player.getUniqueId(), player.getLocation());

            if (oldHome != null) {
                player.sendMessage(prefix + "Your home has been updated to your current location!");
            } else {
                player.sendMessage(prefix + "Home set successfully!");
            }

            playSetHomeEffect(player);
            return true;
        });

        getCommand("home").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + "Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            if (!homes.containsKey(player.getUniqueId())) {
                player.sendMessage(prefix + "You don't have a home set! Use /sethome to set one.");
                return true;
            }

            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = ((cooldowns.get(player.getUniqueId()) + (cooldownTime * 1000)) - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    player.sendMessage(prefix + "You must wait " + ChatColor.YELLOW + timeLeft + ChatColor.GRAY + " seconds before teleporting home again.");
                    return true;
                }
            }

            teleportWithEffect(player, homes.get(player.getUniqueId()));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            return true;
        });
    }

    private void playSetHomeEffect(Player player) {
        Location loc = player.getLocation();
        player.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        player.spawnParticle(Particle.ENCHANT, loc.add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.5);
    }

    private void teleportWithEffect(Player player, Location location) {
        Location startLoc = player.getLocation().clone();

        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (player.getLocation().distance(startLoc) > 0.5) {
                    player.sendMessage(prefix + ChatColor.RED + "Teleport cancelled - you moved!");
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 1, 1);
                    player.sendMessage(prefix + "Teleporting home in " + ChatColor.YELLOW + countdown + ChatColor.GRAY + " seconds...");
                    countdown--;
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
                    player.spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 100, 0.5, 0.5, 0.5, 0.1);
                    player.teleport(location);
                    player.sendMessage(prefix + ChatColor.GREEN + "Welcome home!");
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    @Override
    public void onDisable() {
        homes.clear();
        cooldowns.clear();
    }
}
