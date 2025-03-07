// includes: /npc create <entity>, creates an npc who gives you a quest
package me.tibo.npcplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class Npcplugin extends JavaPlugin implements TabExecutor, Listener {

    private final Map<Player, Boolean> questStatus = new HashMap<>();

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("npc");
        if (command != null) {
            command.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        if (args[0].equalsIgnoreCase("create") && args.length > 1) {
            return handleNpcCreate(sender, args);
        } else if (args[0].equalsIgnoreCase("quest") && args.length > 1) {
            return handleQuestCommands(sender, args);
        }

        return false;
    }

    private boolean handleNpcCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        String typeString = args[1].toUpperCase();
        try {
            EntityType type = EntityType.valueOf(typeString);
            if (!type.isAlive()) {
                player.sendMessage("Invalid entity type!");
                return true;
            }
            Location location = player.getLocation();
            LivingEntity npc = (LivingEntity) player.getWorld().spawnEntity(location, type);
            npc.setCustomName(typeString + " NPC");
            npc.setCustomNameVisible(true);
            npc.setPersistent(true);
            npc.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));

            
            npc.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            npc.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);

            player.sendMessage("NPC of type " + typeString + " created successfully!");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid entity type!");
        }
        return true;
    }

    private boolean handleQuestCommands(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("reset")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        if (questStatus.containsKey(player)) {
            questStatus.put(player, false);
            player.sendMessage("Your quest has been reset.");
        } else {
            player.sendMessage("You have not started a quest yet.");
        }

        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getRightClicked();
            if (entity.getCustomName() != null && entity.getCustomName().contains("NPC")) {
                Player player = event.getPlayer();
                displayTypingEffect(player, entity, "Hi I am an NPC created by Tibo!");


                if (!questStatus.getOrDefault(player, false)) {
                    displayTypingEffect(player, entity, "Want to complete a quest for me? Kill 1 cow, then come back to me.");
                    questStatus.put(player, false);
                } else {
                    displayTypingEffect(player, entity, "Thank you for completing the quest!");
                    givePlayerDiamond(player);
                    questStatus.put(player, true);
                }

                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof LivingEntity && event.getEntity().getCustomName() != null && event.getEntity().getCustomName().contains("NPC")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() == EntityType.COW) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (questStatus.getOrDefault(player, false) == false) {
                    Player killer = event.getEntity().getKiller();
                    if (killer != null && killer.equals(player)) {
                        questStatus.put(player, true);
                        displayQuestCompletion(player);
                    }
                }
            }
        }
    }

    private void displayTypingEffect(Player player, LivingEntity entity, String message) {
        new BukkitRunnable() {
            int index = 0;
            StringBuilder currentText = new StringBuilder();

            @Override
            public void run() {
                if (index < message.length()) {
                    currentText.append(message.charAt(index));
                    entity.setCustomName("§e" + currentText.toString());
                    index++;
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L); 
    }

    private void displayQuestCompletion(Player player) {
        player.sendMessage("Quest completed! Return to the NPC for your reward.");
    }

    private void givePlayerDiamond(Player player) {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));
        player.sendMessage("You have completed the quest and received a diamond!");
    }
}
