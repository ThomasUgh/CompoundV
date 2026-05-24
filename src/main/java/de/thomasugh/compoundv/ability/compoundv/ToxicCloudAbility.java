package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ToxicCloudAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cloudCooldown = new HashMap<>();
    private final Map<UUID, Long> vomitCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();

    public ToxicCloudAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "toxic_cloud"; }
    @Override public String getDisplayName() { return "Toxic Cloud"; }
    @Override public int getColor() { return 0x5EE85E; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.toxic_cloud.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.toxic_cloud.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        cloudCooldown.remove(uuid);
        vomitCooldown.remove(uuid);
        lastHandledAt.remove(uuid);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.POISON);
    }

    @Override
    public void onTick(Player player) {
        player.removePotionEffect(PotionEffects.POISON);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = cloudCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toxic_cloud.cloud_cooldown", "seconds", Long.toString(seconds)));
            return;
        }
        cloudCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.toxic_cloud.cloud_cooldown_ms", 120000L));
        releaseCloud(player);
    }

    public void shootVomit(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long now = System.currentTimeMillis();
        long readyAt = vomitCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toxic_cloud.vomit_cooldown", "seconds", Long.toString(seconds)));
            return;
        }
        vomitCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.toxic_cloud.vomit_cooldown_ms", 10000L));

        double range = plugin.getConfig().getDouble("abilities.toxic_cloud.vomit_range", 5.0);
        double damage = plugin.getConfig().getDouble("abilities.toxic_cloud.vomit_damage_hearts", 1.5) * 2.0;
        int poisonTicks = plugin.getConfig().getInt("abilities.toxic_cloud.vomit_poison_ticks", 80);
        int amplifier = plugin.getConfig().getInt("abilities.toxic_cloud.poison_amplifier", 3);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult hit = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.45,
                entity -> entity != player && entity instanceof LivingEntity);
        double distance = hit != null && hit.getHitPosition() != null ? hit.getHitPosition().distance(eye.toVector()) : range;
        renderVomit(world, eye, dir, distance);
        world.playSound(player.getLocation(), Sound.ENTITY_SLIME_ATTACK, 0.8f, 0.7f);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(Math.max(0.0, damage), player);
            target.addPotionEffect(new PotionEffect(PotionEffects.POISON, poisonTicks, Math.max(0, amplifier), false, true, true));
        }
    }


    public void handleMeleeHit(Player attacker, LivingEntity target) {
        int poisonTicks = plugin.getConfig().getInt("abilities.toxic_cloud.melee_poison_ticks", 40);
        int amplifier = plugin.getConfig().getInt("abilities.toxic_cloud.melee_poison_amplifier", 0);
        target.addPotionEffect(new PotionEffect(PotionEffects.POISON, Math.max(20, poisonTicks), Math.max(0, amplifier), false, true, true));
        target.getWorld().spawnParticle(Particle.SNEEZE, target.getLocation().add(0, 1, 0), 8, 0.22, 0.28, 0.22, 0.02);
    }

    private void releaseCloud(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        double radius = plugin.getConfig().getDouble("abilities.toxic_cloud.cloud_radius", 5.0);
        double coneDot = plugin.getConfig().getDouble("abilities.toxic_cloud.cloud_cone_dot", 0.25);
        int poisonTicks = plugin.getConfig().getInt("abilities.toxic_cloud.cloud_poison_ticks", 120);
        int amplifier = plugin.getConfig().getInt("abilities.toxic_cloud.poison_amplifier", 3);
        int affected = 0;

        Location center = eye.clone().add(dir.clone().multiply(radius * 0.55));
        world.spawnParticle(Particle.DUST, center, 110, radius * 0.35, 1.1, radius * 0.35, 0,
                new Particle.DustOptions(Color.fromRGB(74, 210, 72), 1.35f));
        world.spawnParticle(Particle.SMOKE, center, 75, radius * 0.35, 0.9, radius * 0.35, 0.035);
        world.playSound(center, Sound.ENTITY_CREEPER_PRIMED, 0.55f, 1.55f);

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            Vector toTarget = target.getLocation().toVector().subtract(eye.toVector());
            if (toTarget.lengthSquared() > radius * radius) continue;
            if (toTarget.normalize().dot(dir) < coneDot) continue;
            target.addPotionEffect(new PotionEffect(PotionEffects.POISON, poisonTicks, Math.max(0, amplifier), false, true, true));
            affected++;
        }
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toxic_cloud.cloud_released", "targets", Integer.toString(affected)));
    }

    private void renderVomit(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(92, 230, 75), 0.95f);
        for (double d = 0.45; d <= distance; d += 0.20) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 3, 0.05, 0.05, 0.05, 0, green);
            if (((int) (d * 10)) % 4 == 0) world.spawnParticle(Particle.SNEEZE, point, 1, 0.04, 0.04, 0.04, 0.01);
        }
    }
}
