// simple koth plugin
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
    private final Map<UUID, Integer> killStreak = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("koth").setExecutor((sender, command, label, args) -> {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /koth <start|stop>");
                return true;
            }

            if (args[0].equalsIgnoreCase("start") && sender instanceof Player) {
                if (eventActive) {
                    sender.sendMessage(ChatColor.RED + "Event is already running!");
                    return true;
                }

                Player player = (Player) sender;
                hillCenter = player.getLocation();
                playerPoints.clear();
                playersInZone.clear();
                killStreak.clear();
                eventActive = true;

                Bukkit.broadcastMessage(ChatColor.GREEN + "KOTH has started at " + 
                    String.format("X: %d, Y: %d, Z: %d", 
                    hillCenter.getBlockX(), 
                    hillCenter.getBlockY(), 
                    hillCenter.getBlockZ()));
                
                startPointTask();
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                if (!eventActive) {
                    sender.sendMessage(ChatColor.RED + "No event is running!");
                    return true;
                }

                UUID winner = findCurrentLeader();
                if (winner != null) {
                    Player winningPlayer = Bukkit.getPlayer(winner);
                    if (winningPlayer != null) {
                        Bukkit.broadcastMessage(ChatColor.GOLD + winningPlayer.getName() + 
                            " wins with " + playerPoints.get(winner) + " points!");
                    }
                }

                eventActive = false;
                Bukkit.broadcastMessage(ChatColor.RED + "KOTH has ended");
                return true;
            }

            return false;
        });
    }

    private UUID findCurrentLeader() {
        return playerPoints.entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
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

                    if (newPoints % 10 == 0) {
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
