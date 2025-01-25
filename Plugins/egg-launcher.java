package me.Tibo.java;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class Java extends JavaPlugin implements Listener {

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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().toString().contains("RIGHT_CLICK")) {
            Player player = event.getPlayer();

            if (player.getInventory().getItemInMainHand().getType() == Material.STICK) {
                Egg egg = player.launchProjectile(Egg.class);

                Vector velocity = player.getLocation().getDirection().multiply(4);
                egg.setVelocity(velocity);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg) {
            Entity hitEntity = event.getHitEntity();

            if (hitEntity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) hitEntity;
                livingEntity.damage(5.0);

                if (livingEntity instanceof Player) {
                    ((Player) livingEntity).sendMessage("you were hit");
                }
            }
        }
    }
}
