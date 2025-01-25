package me.Tibo.koth;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class Bounty extends JavaPlugin implements Listener {

    private boolean eventActive = false;
    private Location hillCenter;
    private int hillRadius = 10;
    private final HashMap<UUID, Integer> playerPoints = new HashMap<>();
    private final List<Player> playersInZone = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("koth").setExecutor((sender, command, label, args) -> {
            if (args.length < 1) {
                sender.sendMessage("/koth start | /koth stop");
                return true;
            }

            if (args[0].equalsIgnoreCase("start") && sender instanceof Player) {
                Player player = (Player) sender;
                hillCenter = new Location(player.getWorld(), 10, 64, 10); // Fixed coordinates for the KOTH area
                hillRadius = 10; // Default radius
                playerPoints.clear();
                playersInZone.clear();
                eventActive = true;

                Bukkit.broadcastMessage(ChatColor.GREEN + "Koth has started");
                startPointTask();
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                eventActive = false;
                Bukkit.broadcastMessage(ChatColor.RED + "Koth has ended");
                return true;
            }

            return false;
        });
    }

    private void startPointTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) {
                    this.cancel();
                    return;
                }

                for (Player player : playersInZone) {
                    UUID playerUUID = player.getUniqueId();
                    int currentPoints = playerPoints.getOrDefault(playerUUID, 0);
                    int newPoints = currentPoints + 1;
                    playerPoints.put(playerUUID, newPoints);

                    if (newPoints % 10 == 0) { // Announce every 10 points
                        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " has reached " + newPoints + " points!");
                    }
                }

                checkWinCondition();
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void checkWinCondition() {
        playerPoints.forEach((uuid, points) -> {
            if (points >= 100) {
                Player winner = Bukkit.getPlayer(uuid);
                if (winner != null) {
                    Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " has won the King of the Hill event!");
                    eventActive = false;
                }
            }
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!eventActive) return;

        Player player = event.getPlayer();
        Location playerLoc = player.getLocation();

        boolean inZone = playerLoc.getWorld().equals(hillCenter.getWorld())
                && playerLoc.distance(hillCenter) <= hillRadius;

        if (inZone && !playersInZone.contains(player)) {
            playersInZone.add(player);
            player.sendMessage(ChatColor.GREEN + "You have entered koth");
        } else if (!inZone && playersInZone.contains(player)) {
            playersInZone.remove(player);
            player.sendMessage(ChatColor.RED + "You left koth");
        }
    }

    @Override
    public void onDisable() {
        eventActive = false;
        playerPoints.clear();
        playersInZone.clear();
    }
}
