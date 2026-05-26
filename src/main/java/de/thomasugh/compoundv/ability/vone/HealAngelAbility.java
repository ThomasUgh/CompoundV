package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class HealAngelAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey healthKey;
    private final Map<UUID, AngelCommand> commandedTargets = new HashMap<>();
    private final Map<UUID, Long> commandCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> healCooldownUntil = new HashMap<>();
    private int tick;

    public HealAngelAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "heal_angel_hearts");
    }

    @Override public String getId() { return "heal_angel"; }
    @Override public String getDisplayName() { return "Heal Angel"; }
    @Override public int getColor() { return 0xFFF4D6; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.heal_angel.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.heal_angel.resistance_level", 2);
        int regeneration = plugin.getConfig().getInt("abilities.heal_angel.regeneration_level", 2);
        double extraHearts = plugin.getConfig().getDouble("abilities.heal_angel.extra_hearts", 5.0);

        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION, Integer.MAX_VALUE, Math.max(0, regeneration - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID owner = player.getUniqueId();
        commandedTargets.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(owner));
        commandCooldownUntil.remove(owner);
        healCooldownUntil.remove(owner);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0.0);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.REGENERATION);
    }

    @Override
    public void onToggle(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = commandCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("heal_angel.command_cooldown", "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 0.65f);
            return;
        }

        double range = plugin.getConfig().getDouble("abilities.heal_angel.command_range", 35.0);
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection().normalize(),
                range, FluidCollisionMode.NEVER, true, 0.65,
                entity -> entity instanceof LivingEntity && !(entity instanceof Player) && entity != player);
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("heal_angel.no_target"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.75f);
            return;
        }

        long durationMs = plugin.getConfig().getLong("abilities.heal_angel.command_duration_ms", 120000L);
        long cooldownMs = plugin.getConfig().getLong("abilities.heal_angel.command_cooldown_ms", 30000L);
        commandedTargets.put(target.getUniqueId(), new AngelCommand(player.getUniqueId(), now + Math.max(1000L, durationMs)));
        commandCooldownUntil.put(player.getUniqueId(), now + Math.max(0L, cooldownMs));

        Location loc = target.getLocation().add(0, Math.min(1.5, Math.max(0.7, target.getEyeHeight())), 0);
        target.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, (int) Math.min(Integer.MAX_VALUE, durationMs / 50L), 0, false, false, false));
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 36, 0.35, 0.45, 0.35, 0.12);
        target.getWorld().spawnParticle(Particle.END_ROD, loc, 22, 0.28, 0.35, 0.28, 0.04);
        target.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.55f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("heal_angel.command_released"));
    }

    @Override
    public void onTick(Player player) {
        if (player.isDead()) {
            commandedTargets.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(player.getUniqueId()));
            return;
        }

        tick++;
        if (tick % 20 != 0) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, AngelCommand>> iterator = commandedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AngelCommand> entry = iterator.next();
            AngelCommand command = entry.getValue();
            if (!command.ownerId().equals(player.getUniqueId()) || command.expiresAt() < now) {
                iterator.remove();
                continue;
            }

            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity controlled) || controlled.isDead() || !controlled.isValid()) {
                iterator.remove();
                continue;
            }

            LivingEntity target = findNearestEnemy(player, controlled);
            if (target == null) continue;

            if (controlled instanceof Mob mob) {
                mob.setTarget(target);
            }

            Location loc = controlled.getLocation().add(0, Math.min(1.2, Math.max(0.6, controlled.getEyeHeight())), 0);
            controlled.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.22, 0.22, 0.22, 0.01);

            if (controlled.getLocation().distanceSquared(target.getLocation()) <= 3.2 * 3.2) {
                double damage = plugin.getConfig().getDouble("abilities.heal_angel.command_pet_damage_hearts", 1.0) * 2.0;
                target.damage(Math.max(0.0, damage), player);
                Vector push = target.getLocation().toVector().subtract(controlled.getLocation().toVector());
                if (push.lengthSquared() > 0.0001) target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.18).setY(0.10)));
            }
        }
    }

    public boolean handleHealingHit(Player healer, LivingEntity target) {
        if (!healer.isSneaking()) return false;

        UUID uuid = healer.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = healCooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(healer, plugin.getLocaleManager().msg("heal_angel.heal_cooldown", "seconds", Long.toString(seconds)));
            healer.playSound(healer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 1.4f);
            return true;
        }

        long cooldownMs = plugin.getConfig().getLong("abilities.heal_angel.heal_cooldown_ms", 1000L);
        healCooldownUntil.put(uuid, now + Math.max(0L, cooldownMs));

        double ratio = plugin.getConfig().getDouble("abilities.heal_angel.heal_percent_per_hit", 0.10);
        double minHearts = plugin.getConfig().getDouble("abilities.heal_angel.heal_min_hearts", 1.0);
        double maxHealth = Math.max(1.0, target.getMaxHealth());
        double healAmount = Math.max(minHearts * 2.0, maxHealth * Math.max(0.0, ratio));
        double newHealth = Math.min(maxHealth, target.getHealth() + healAmount);
        target.setHealth(newHealth);

        Location loc = target.getLocation().add(0, Math.min(1.5, Math.max(0.7, target.getEyeHeight())), 0);
        World world = target.getWorld();
        world.spawnParticle(Particle.HEART, loc, 7, 0.32, 0.28, 0.32, 0.02);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 18, 0.28, 0.28, 0.28, 0.08);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.25f, 1.85f);
        MessageUtil.sendActionBar(healer, plugin.getLocaleManager().msg("heal_angel.healed"));
        return true;
    }

    public boolean isCommandedByOwner(Entity entity, Player owner) {
        AngelCommand command = commandedTargets.get(entity.getUniqueId());
        if (command == null) return false;
        if (!command.ownerId().equals(owner.getUniqueId())) return false;
        if (command.expiresAt() < System.currentTimeMillis() || owner.isDead()) {
            commandedTargets.remove(entity.getUniqueId());
            return false;
        }
        return true;
    }

    private LivingEntity findNearestEnemy(Player owner, LivingEntity controlled) {
        double radius = plugin.getConfig().getDouble("abilities.heal_angel.command_attack_radius", 20.0);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : controlled.getWorld().getNearbyEntities(controlled.getLocation(), radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (living.equals(controlled) || living.equals(owner)) continue;
            if (living.isDead() || !living.isValid()) continue;
            double distance = living.getLocation().distanceSquared(controlled.getLocation());
            if (distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    private record AngelCommand(UUID ownerId, long expiresAt) { }
}
