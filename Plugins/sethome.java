package me.Tibo.sethome;
import org.bukkit.Bukkit;
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

    @Override
    public void onEnable() {
        getCommand("sethome").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                homes.put(player.getUniqueId(), player.getLocation());
                player.sendMessage("home set successfully");
                return true;
            }
            return false;
        });

        getCommand("home").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!homes.containsKey(player.getUniqueId())) {
                    player.sendMessage("You don't have a home set.");
                    return true;
                }
                if (cooldowns.containsKey(player.getUniqueId())) {
                    long remaining = cooldownTime - ((System.currentTimeMillis() - cooldowns.get(player.getUniqueId())) / 1000);
                    if (remaining > 0) {
                        player.sendMessage("You must wait " + remaining + " seconds before teleporting home again.");
                        return true;
                    }
                }
                teleportWithEffect(player, homes.get(player.getUniqueId()));
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                return true;
            }
            return false;
        });
    }

    private void teleportWithEffect(Player player, Location location) {
        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50);
                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 1, 1);
                    player.sendMessage("teleporting home in " + countdown + " seconds");
                    countdown--;
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
                    player.teleport(location);
                    player.sendMessage("Welcome home!");
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    @Override
    public void onDisable() {
    }
}
