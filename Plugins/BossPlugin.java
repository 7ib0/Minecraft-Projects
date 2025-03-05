package me.tibo.bossplugin;

import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class Bossplugin extends JavaPlugin implements Listener {

    private List<BukkitTask> activeTasks = new ArrayList<>();
    private Map<UUID, BossBar> bossBars = new HashMap<>();
    private Set<UUID> angeredBosses = new HashSet<>();
    private Random random = new Random();

    private double bossMaxHealth;
    private int bossStrengthLevel;
    private int bossSpeedLevel;
    private float explosionPower;
    private int angerThreshold;
    private int fireballChance;
    private int arrowChance;
    private int jumpChance;
    private double jumpHeight;
    private int attackInterval;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BossPlugin enabled!");


        getCommand("boss").setExecutor(this);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        bossMaxHealth = config.getDouble("boss.maxHealth", 100.0);
        bossStrengthLevel = config.getInt("boss.strengthLevel", 0);
        bossSpeedLevel = config.getInt("boss.speedLevel", 0);
        explosionPower = (float) config.getDouble("boss.explosionPower", 2.0);
        angerThreshold = config.getInt("boss.angerThreshold", 25);
        fireballChance = config.getInt("boss.attacks.fireballChance", 30);
        arrowChance = config.getInt("boss.attacks.arrowChance", 25);
        jumpChance = config.getInt("boss.attacks.jumpChance", 35);
        jumpHeight = config.getDouble("boss.attacks.jumpHeight", 3.0);
        attackInterval = config.getInt("boss.attackInterval", 35);
    }

    @Override
    public void onDisable() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
        cancelActiveTasks();
        getLogger().info("BossPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("boss")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("bossplugin.spawn")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to spawn the boss!");
                return true;
            }

            spawnBoss(player);
            return true;
        }
        return false;
    }

    private void spawnBoss(Player player) {
        Location spawnLocation = player.getLocation();
        WitherSkeleton bossEntity = (WitherSkeleton) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.WITHER_SKELETON);


        bossEntity.setCustomName(ChatColor.RED + "§lBoss");
        bossEntity.setCustomNameVisible(true);
        bossEntity.setMaxHealth(bossMaxHealth);
        bossEntity.setHealth(bossMaxHealth);


        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        bossEntity.getEquipment().setItemInMainHand(sword);

        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        bossEntity.getEquipment().setHelmet(helmet);


        bossEntity.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        bossEntity.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        bossEntity.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));


        bossEntity.getEquipment().setHelmetDropChance(0);
        bossEntity.getEquipment().setChestplateDropChance(0);
        bossEntity.getEquipment().setLeggingsDropChance(0);
        bossEntity.getEquipment().setBootsDropChance(0);
        bossEntity.getEquipment().setItemInMainHandDropChance(0);


        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, bossStrengthLevel));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, bossSpeedLevel));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));


        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.GOLD + "§lBoss",
                BarColor.RED,
                BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        bossBars.put(bossEntity.getUniqueId(), bossBar);


        World world = spawnLocation.getWorld();
        if (world != null) {
            world.strikeLightningEffect(spawnLocation);
            world.playSound(spawnLocation, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            world.spawnParticle(Particle.EXPLOSION, spawnLocation, 1);
            world.spawnParticle(Particle.FLAME, spawnLocation, 50, 0.5, 1, 0.5, 0.1);
            Bukkit.broadcastMessage(ChatColor.RED + "§l" + player.getName() + " has summoned the Boss!");
        }

        startBossAttacks(bossEntity);
    }

    private void startBossAttacks(WitherSkeleton boss) {
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> {
            if (!boss.isDead()) {
                Location bossLoc = boss.getLocation();
                World world = boss.getWorld();


                Player target = getNearestPlayer(boss);
                if (target != null) {
                    boss.setTarget(target);

                    if (random.nextInt(100) < fireballChance) {
                        Fireball fireball = boss.getWorld().spawn(boss.getEyeLocation(), Fireball.class);
                        fireball.setYield(explosionPower);
                        Vector direction = target.getLocation().subtract(boss.getLocation()).toVector().normalize();
                        fireball.setDirection(direction);
                        world.spawnParticle(Particle.FLAME, boss.getEyeLocation(), 20, 0.2, 0.2, 0.2, 0.1);
                    }

                    if (random.nextInt(100) < arrowChance) {
                        Arrow arrow = boss.launchProjectile(Arrow.class);
                        arrow.setVelocity(target.getLocation().subtract(boss.getLocation()).toVector().normalize().multiply(2));
                        arrow.setDamage(8.0);
                        arrow.setGlowing(true);
                    }

                    if (random.nextInt(100) < jumpChance) {
                        Vector jump = new Vector(0, jumpHeight, 0);
                        boss.setVelocity(jump);
                        world.spawnParticle(Particle.CLOUD, bossLoc, 30, 0.5, 0, 0.5, 0.1);

                        getServer().getScheduler().runTaskLater(this, () -> {
                            Location landLoc = boss.getLocation();
                            createExplosionPattern(landLoc, world);
                        }, 20L);
                    }
                }
            }
        }, attackInterval, attackInterval);

        activeTasks.add(task);
    }

    private Player getNearestPlayer(Entity entity) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player player : entity.getWorld().getPlayers()) {
            double distance = player.getLocation().distance(entity.getLocation());
            if (distance < minDistance && !player.isDead() && player.getGameMode() == GameMode.SURVIVAL) {
                minDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    private void createExplosionPattern(Location center, World world) {
        world.createExplosion(center, explosionPower, false, false);

        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            Location loc = center.clone().add(
                    2 * Math.cos(angle),
                    0,
                    2 * Math.sin(angle)
            );
            world.createExplosion(loc, explosionPower * 0.7f, false, false);
        }

        world.spawnParticle(Particle.EXPLOSION, center, 5, 2, 0, 2, 0.1);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntity().getCustomName() != null &&
                event.getEntity().getCustomName().contains("Boss")) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {

                killer.getInventory().addItem(
                        new ItemStack(Material.DIAMOND_BLOCK, 5),
                        new ItemStack(Material.NETHERITE_INGOT, 2),
                        new ItemStack(Material.GOLDEN_APPLE, 3),
                        new ItemStack(Material.EXPERIENCE_BOTTLE, 5)
                );

                event.setDroppedExp(1000);


                World world = killer.getWorld();
                Location loc = killer.getLocation();
                world.strikeLightningEffect(loc);
                world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 100, 1, 1, 1, 0.5);

                Bukkit.broadcastMessage(ChatColor.GOLD + "§l" + killer.getName() +
                        " has defeated the Boss! §6§lCONGRATULATIONS!");
            }

            cleanup(event.getEntity().getUniqueId());
        }
    }

    private void cleanup(UUID bossId) {
        cancelActiveTasks();
        angeredBosses.remove(bossId);
        if (bossBars.containsKey(bossId)) {
            bossBars.get(bossId).removeAll();
            bossBars.remove(bossId);
        }
    }

    private void cancelActiveTasks() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof WitherSkeleton) {
            WitherSkeleton boss = (WitherSkeleton) event.getEntity();
            if (boss.getCustomName() != null && boss.getCustomName().contains("Boss")) {

                if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                        event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                        event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                    event.setCancelled(true);
                    if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                        boss.getWorld().createExplosion(boss.getLocation(), explosionPower, false, false);
                    }
                    return;
                }


                updateBossBar(boss, event.getFinalDamage());


                checkAngerMode(boss, event.getFinalDamage());
            }
        }
    }

    private void updateBossBar(WitherSkeleton boss, double damage) {
        if (bossBars.containsKey(boss.getUniqueId())) {
            BossBar bossBar = bossBars.get(boss.getUniqueId());
            double newHealth = boss.getHealth() - damage;
            double progress = Math.max(0, Math.min(1, newHealth / boss.getMaxHealth()));
            bossBar.setProgress(progress);
        }
    }

    private void checkAngerMode(WitherSkeleton boss, double damage) {
        double healthPercentage = (boss.getHealth() - damage) / boss.getMaxHealth() * 100;
        if (healthPercentage <= angerThreshold && !angeredBosses.contains(boss.getUniqueId())) {
            angeredBosses.add(boss.getUniqueId());


            Bukkit.broadcastMessage(ChatColor.DARK_RED + "§l⚠ THE BOSS HAS ENTERED RAGE MODE! ⚠");


            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, bossStrengthLevel + 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, bossSpeedLevel + 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0));


            World world = boss.getWorld();
            Location loc = boss.getLocation();
            world.strikeLightningEffect(loc);
            world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
            world.spawnParticle(Particle.FLAME, loc, 100, 1, 1, 1, 0.1);


            if (bossBars.containsKey(boss.getUniqueId())) {
                BossBar bossBar = bossBars.get(boss.getUniqueId());
                bossBar.setColor(BarColor.PURPLE);
                bossBar.setTitle(ChatColor.DARK_RED + "§l☠ ENRAGED BOSS ☠");
            }
        }
    }
}
