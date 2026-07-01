package com.example.stunpop;

import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StunPopPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, StunRecord> recentShieldDisables = new HashMap<>();
    private int currentTick = 0;

    private int followUpWindowTicks;
    private double verticalVelocity;
    private boolean addHorizontalVelocity;
    private double horizontalVelocity;
    private String followUpWeapon;
    private double minimumAttackerFallDistance;
    private boolean clearNoDamageTicksOnShieldDisable;
    private boolean clearNoDamageTicksOnFollowUp;
    private boolean requireSameAttacker;
    private int shieldCooldownTicks;
    private boolean playSound;
    private boolean spawnParticles;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> currentTick++, 1L, 1L);

        getLogger().info("StunPop enabled. Paper shield setting should also be true: unsupported-settings.skip-vanilla-damage-tick-when-shield-blocked");
    }

    @Override
    public void onDisable() {
        recentShieldDisables.clear();
    }

    private void loadSettings() {
        reloadConfig();
        followUpWindowTicks = Math.max(1, getConfig().getInt("follow-up-window-ticks", 14));
        verticalVelocity = clamp(getConfig().getDouble("vertical-velocity", 0.62), 0.0, 3.0);
        addHorizontalVelocity = getConfig().getBoolean("add-horizontal-velocity", true);
        horizontalVelocity = clamp(getConfig().getDouble("horizontal-velocity", 0.18), 0.0, 3.0);
        followUpWeapon = getConfig().getString("follow-up-weapon", "ANY").toUpperCase(Locale.ROOT);
        minimumAttackerFallDistance = Math.max(0.0, getConfig().getDouble("minimum-attacker-fall-distance", 0.0));
        clearNoDamageTicksOnShieldDisable = getConfig().getBoolean("clear-no-damage-ticks-on-shield-disable", true);
        clearNoDamageTicksOnFollowUp = getConfig().getBoolean("clear-no-damage-ticks-on-follow-up", true);
        requireSameAttacker = getConfig().getBoolean("require-same-attacker", true);
        shieldCooldownTicks = getConfig().getInt("shield-cooldown-ticks", -1);
        playSound = getConfig().getBoolean("play-sound", true);
        spawnParticles = getConfig().getBoolean("spawn-particles", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) {
            return;
        }

        Player victim = event.getPlayer();

        if (shieldCooldownTicks >= 0) {
            event.setCooldown(Math.max(1, shieldCooldownTicks));
        }

        StunRecord record = new StunRecord(attacker.getUniqueId(), currentTick);
        recentShieldDisables.put(victim.getUniqueId(), record);

        if (clearNoDamageTicksOnShieldDisable) {
            victim.setNoDamageTicks(0);
            Bukkit.getScheduler().runTask(this, () -> {
                if (victim.isOnline()) {
                    victim.setNoDamageTicks(0);
                }
            });
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            StunRecord existing = recentShieldDisables.get(victim.getUniqueId());
            if (existing == record) {
                recentShieldDisables.remove(victim.getUniqueId());
            }
        }, followUpWindowTicks + 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFollowUpHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        StunRecord record = recentShieldDisables.get(victim.getUniqueId());
        if (record == null) {
            return;
        }

        // Do not trigger on the same tick as the shield-disable hit itself.
        if (currentTick <= record.createdTick()) {
            return;
        }

        if (currentTick - record.createdTick() > followUpWindowTicks) {
            recentShieldDisables.remove(victim.getUniqueId());
            return;
        }

        if (requireSameAttacker && !record.attackerId().equals(attacker.getUniqueId())) {
            return;
        }

        if (!matchesRequiredWeapon(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        if (minimumAttackerFallDistance > 0.0 && attacker.getFallDistance() < minimumAttackerFallDistance) {
            return;
        }

        if (clearNoDamageTicksOnFollowUp) {
            victim.setNoDamageTicks(0);
        }

        recentShieldDisables.remove(victim.getUniqueId());

        // Apply after vanilla knockback has had a chance to run.
        Bukkit.getScheduler().runTask(this, () -> applyPop(attacker, victim));
    }

    private void applyPop(Player attacker, Player victim) {
        if (!victim.isOnline() || victim.isDead()) {
            return;
        }

        Vector velocity = victim.getVelocity();
        velocity.setY(Math.max(velocity.getY(), verticalVelocity));

        if (addHorizontalVelocity && horizontalVelocity > 0.0) {
            Vector direction = attacker.getLocation().getDirection();
            direction.setY(0.0);
            if (direction.lengthSquared() > 0.0001) {
                direction.normalize().multiply(horizontalVelocity);
                velocity.add(direction);
            }
        }

        victim.setVelocity(velocity);

        if (playSound) {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.15f);
        }
        if (spawnParticles) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0.0, 1.0, 0.0), 12, 0.25, 0.25, 0.25, 0.05);
        }
    }

    private boolean matchesRequiredWeapon(ItemStack item) {
        if (followUpWeapon.equals("ANY") || followUpWeapon.equals("NONE")) {
            return true;
        }

        Material type = item == null ? Material.AIR : item.getType();
        return switch (followUpWeapon) {
            case "MACE" -> type == Material.MACE;
            case "AXE" -> isAxe(type);
            case "SWORD" -> isSword(type);
            default -> true;
        };
    }

    private boolean isAxe(Material material) {
        return material == Material.WOODEN_AXE
                || material == Material.STONE_AXE
                || material == Material.IRON_AXE
                || material == Material.GOLDEN_AXE
                || material == Material.DIAMOND_AXE
                || material == Material.NETHERITE_AXE;
    }

    private boolean isSword(Material material) {
        return material == Material.WOODEN_SWORD
                || material == Material.STONE_SWORD
                || material == Material.IRON_SWORD
                || material == Material.GOLDEN_SWORD
                || material == Material.DIAMOND_SWORD
                || material == Material.NETHERITE_SWORD;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§aStunPop config reloaded.");
            return true;
        }
        sender.sendMessage("§eUsage: /stunpop reload");
        return true;
    }

    private record StunRecord(UUID attackerId, int createdTick) {
    }
}
