package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TheVeteranAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Set<UUID>       activeBursts  = new HashSet<>();
    private final Map<UUID, Long> burstCooldown = new HashMap<>();

    public TheVeteranAbility(CompoundVPlugin p) { plugin = p; }

    @Override public String    getId()          { return "the_veteran"; }
    @Override public String    getDisplayName() { return "The Veteran"; }
    @Override public TextColor getColor()       { return TextColor.color(0xC0C0C0); }

    @Override
    public void apply(Player p) {
        int str = plugin.getConfig().getInt("abilities.the_veteran.strength_level",   5);
        int res = plugin.getConfig().getInt("abilities.the_veteran.resistance_level", 3);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, str - 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, res - 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
    }

    @Override
    public void remove(Player p) {
        UUID u = p.getUniqueId();
        activeBursts.remove(u);
        burstCooldown.remove(u);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    public boolean isBurstActive(Player p) { return activeBursts.contains(p.getUniqueId()); }

    public void startBurst(Player p) {
        UUID u = p.getUniqueId();

        long cd  = plugin.getConfig().getLong("abilities.the_veteran.burst_cooldown_ms", 45_000L);
        long now = System.currentTimeMillis();
        long last = burstCooldown.getOrDefault(u, 0L);
        if (now - last < cd) {
            long secs = (cd - (now - last)) / 1000 + 1;
            p.sendActionBar(plugin.getLocaleManager().msg(
                    "veteran.cooldown", "seconds", Long.toString(secs)));
            return;
        }
        if (activeBursts.contains(u)) return;

        activeBursts.add(u);
        burstCooldown.put(u, now);

        p.sendActionBar(plugin.getLocaleManager().msg("veteran.charge_start"));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.45f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.45f, 0.6f);
        chargeThenFire(p, u);
    }

    private void chargeThenFire(Player shooter, UUID uuid) {
        int chargeTicks = plugin.getConfig().getInt("abilities.the_veteran.charge_duration_ticks", 100);
        int periodTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.charge_period_ticks", 5));

        new BukkitRunnable() {
            int age = 0;

            @Override public void run() {
                if (!shooter.isOnline() || !activeBursts.contains(uuid)) {
                    activeBursts.remove(uuid);
                    cancel();
                    return;
                }

                if (age >= chargeTicks) {
                    shooter.sendActionBar(plugin.getLocaleManager().msg("veteran.burst_start"));
                    shooter.playSound(shooter.getLocation(), Sound.ITEM_TOTEM_USE,            0.8f, 0.65f);
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_WITHER_SPAWN,       1.0f, 1.45f);
                    shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.65f);
                    triggerGroundZeroExplosion(shooter);
                    fireChestBeam(shooter, uuid);
                    cancel();
                    return;
                }

                animateCharge(shooter, age, chargeTicks);
                if (age % 20 == 0) {
                    long seconds = Math.max(1, (long) Math.ceil((chargeTicks - age) / 20.0));
                    shooter.sendActionBar(plugin.getLocaleManager().msg(
                            "veteran.charge_progress", "seconds", Long.toString(seconds)));
                }

                age += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }

    private void animateCharge(Player shooter, int age, int chargeTicks) {
        World w = shooter.getWorld();
        Location chest = shooter.getLocation().add(0, 1.25, 0);
        double progress = Math.min(1.0, Math.max(0.0, age / Math.max(1.0, (double) chargeTicks)));
        double radius = 0.35 + progress * 1.15;
        int particles = 10 + (int) Math.round(progress * 20.0);

        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 225, 40), 1.35f + (float) progress);
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 115, 10), 1.0f + (float) progress * 0.8f);

        w.spawnParticle(Particle.DUST, chest, particles, radius, radius * 0.6, radius, 0, yellow);
        w.spawnParticle(Particle.DUST, chest, Math.max(4, particles / 2), radius * 0.75, radius * 0.45, radius * 0.75, 0, orange);
        w.spawnParticle(Particle.LARGE_SMOKE, chest, 5 + (int) (progress * 12), radius * 0.45, 0.25, radius * 0.45, 0.03 + progress * 0.04);
        if (age % 10 == 0) {
            w.playSound(chest, Sound.BLOCK_BEACON_AMBIENT, 0.45f, (float) (0.45 + progress * 0.45));
        }
    }

    private void triggerGroundZeroExplosion(Player shooter) {
        World w = shooter.getWorld();
        Location base = shooter.getLocation();

        double radius = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_radius", 14.0);
        double maxDamage = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_damage", 500.0);
        double knockback = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_knockback", 7.5);
        boolean setFire = plugin.getConfig().getBoolean("abilities.the_veteran.ground_zero_set_fire", true);

        w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 8.0f, 0.25f);
        w.playSound(base, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 6.0f, 0.35f);
        w.playSound(base, Sound.ENTITY_WITHER_BREAK_BLOCK, 4.0f, 0.35f);
        w.playSound(base, Sound.ITEM_FIRECHARGE_USE, 5.0f, 0.35f);
        w.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.0, 0), 58, 6.2, 2.4, 6.2, 0.0);
        w.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 1.1, 0), 420, 7.5, 2.1, 7.5, 0.17);
        w.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.35, 0), 280, 8.5, 0.28, 8.5, 0.65);
        w.spawnParticle(Particle.FLAME, base.clone().add(0, 0.8, 0), 280, 6.8, 1.4, 6.8, 0.22);
        w.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), 260, 6.2, 1.5, 6.2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 35), 3.1f));

        animateAtomicMushroom(base.clone());

        for (Entity entity : w.getNearbyEntities(base, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(shooter)) continue;

            double distance = Math.max(0.6, target.getLocation().distance(base));
            if (distance > radius) continue;

            double factor = 1.0 - (distance / radius);
            double damage = Math.max(18.0, maxDamage * Math.max(0.25, factor));
            target.damage(damage, shooter);
            if (setFire) target.setFireTicks(220);

            Vector push = target.getLocation().toVector().subtract(base.toVector());
            if (push.lengthSquared() > 0.001) {
                push.normalize().multiply(knockback * Math.max(0.55, factor)).setY(1.6 + factor * 2.0);
                target.setVelocity(push);
            }
        }
    }

    private void animateAtomicMushroom(Location base) {
        World w = base.getWorld();
        if (w == null) return;

        new BukkitRunnable() {
            int age = 0;

            @Override public void run() {
                if (age > 70) {
                    cancel();
                    return;
                }

                double progress = age / 70.0;
                double stemHeight = 1.2 + progress * 9.0;
                double stemRadius = 0.7 + progress * 1.2;
                double capHeight = 5.0 + progress * 5.0;
                double capRadius = 2.6 + progress * 5.8;

                Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 210, 40), 2.2f);
                Particle.DustOptions smoke = new Particle.DustOptions(Color.fromRGB(85, 78, 65), 2.5f);

                Location stem = base.clone().add(0, stemHeight, 0);
                w.spawnParticle(Particle.LARGE_SMOKE, stem, 45, stemRadius, 1.3, stemRadius, 0.08);
                w.spawnParticle(Particle.CLOUD, stem, 36, stemRadius * 0.8, 1.1, stemRadius * 0.8, 0.18);
                w.spawnParticle(Particle.DUST, stem, 28, stemRadius * 0.55, 0.9, stemRadius * 0.55, 0, smoke);

                Location cap = base.clone().add(0, capHeight, 0);
                w.spawnParticle(Particle.LARGE_SMOKE, cap, 80, capRadius, 1.0, capRadius, 0.08);
                w.spawnParticle(Particle.CLOUD, cap, 65, capRadius * 0.85, 0.75, capRadius * 0.85, 0.20);
                w.spawnParticle(Particle.DUST, cap, 34, capRadius * 0.8, 0.5, capRadius * 0.8, 0, yellow);

                if (age % 10 == 0) {
                    w.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.2 + progress * 2.0, 0), 8, 3.5 + progress * 4.0, 0.8, 3.5 + progress * 4.0, 0.0);
                    w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.25f + (float) progress * 0.2f);
                }

                age += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void fireChestBeam(Player shooter, UUID uuid) {
        int durationTicks = plugin.getConfig().getInt("abilities.the_veteran.beam_duration_ticks", 80);
        int periodTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_period_ticks", 2));
        double range = plugin.getConfig().getDouble("abilities.the_veteran.beam_range", 48.0);
        double radius = plugin.getConfig().getDouble("abilities.the_veteran.beam_radius", 1.35);

        double patriotDamage = plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_amount", 10.0);
        double damageMultiplier = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage_multiplier", 5.0);
        double damage = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage_amount", patriotDamage * damageMultiplier);

        int damageEveryTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_damage_interval_ticks", 2));
        boolean breakBlocks = plugin.getConfig().getBoolean("abilities.the_veteran.beam_break_blocks", true);
        boolean igniteBlocks = plugin.getConfig().getBoolean("abilities.the_veteran.beam_ignite_blocks", true);
        int blockAffectEveryTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_block_affect_interval_ticks", 5));
        int maxBlocksPerPulse = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_max_blocks_per_pulse", 4));
        int blockHitsToBreak = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_block_hits_to_break", 5));
        boolean surfaceOnly = plugin.getConfig().getBoolean("abilities.the_veteran.beam_surface_only", true);

        new BukkitRunnable() {
            int age = 0;
            final Map<Block, Integer> weakenedBlocks = new HashMap<>();

            @Override public void run() {
                if (!shooter.isOnline() || !activeBursts.contains(uuid)) {
                    activeBursts.remove(uuid);
                    cancel();
                    return;
                }
                if (age >= durationTicks) {
                    activeBursts.remove(uuid);
                    shooter.sendActionBar(plugin.getLocaleManager().msg("veteran.burst_end"));
                    cancel();
                    return;
                }

                Location chest = shooter.getLocation().add(0, 1.25, 0);
                Vector dir = shooter.getEyeLocation().getDirection().normalize();
                renderAndApplyBeam(shooter, chest, dir, range, radius, damage,
                        age % damageEveryTicks == 0,
                        age % blockAffectEveryTicks == 0,
                        breakBlocks, igniteBlocks, maxBlocksPerPulse, blockHitsToBreak,
                        surfaceOnly, weakenedBlocks);

                if (age % 10 == 0) {
                    shooter.getWorld().playSound(chest, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.7f);
                    shooter.getWorld().playSound(chest, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 0.55f);
                }

                age += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }

    private void renderAndApplyBeam(Player shooter, Location origin, Vector dir,
                                    double range, double radius, double damage,
                                    boolean damageThisTick, boolean affectBlocksThisTick,
                                    boolean breakBlocks, boolean igniteBlocks,
                                    int maxBlocksPerPulse, int blockHitsToBreak,
                                    boolean surfaceOnly, Map<Block, Integer> weakenedBlocks) {
        World w = shooter.getWorld();
        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 220, 35), 2.4f);
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 120, 0), 1.8f);
        Particle.DustOptions whiteHot = new Particle.DustOptions(Color.fromRGB(255, 245, 170), 1.2f);

        RayTraceResult blockHit = w.rayTraceBlocks(origin, dir, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = range;
        if (blockHit != null && blockHit.getHitPosition() != null) {
            effectiveRange = Math.min(range, blockHit.getHitPosition().distance(origin.toVector()) + radius * 1.5);
        }

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        int[] blockBudget = new int[] { maxBlocksPerPulse };

        for (double d = 0.4; d <= effectiveRange; d += 0.65) {
            Location center = origin.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.DUST, center, 4, 0.12, 0.12, 0.12, 0, yellow);
            w.spawnParticle(Particle.FLAME, center, 3, 0.16, 0.16, 0.16, 0.01);
            if (d % 1.95 < 0.65) {
                w.spawnParticle(Particle.DUST, center.clone().add(right.clone().multiply(radius * 0.45)), 2, 0.08, 0.08, 0.08, 0, orange);
                w.spawnParticle(Particle.DUST, center.clone().subtract(right.clone().multiply(radius * 0.45)), 2, 0.08, 0.08, 0.08, 0, orange);
                w.spawnParticle(Particle.DUST, center.clone().add(up.clone().multiply(radius * 0.35)), 1, 0.06, 0.06, 0.06, 0, whiteHot);
            }

            if (affectBlocksThisTick && (breakBlocks || igniteBlocks) && blockBudget[0] > 0) {
                affectBlocksAround(center, radius, breakBlocks, igniteBlocks, surfaceOnly,
                        blockHitsToBreak, blockBudget, weakenedBlocks);
            }
        }

        if (!damageThisTick) return;
        for (Entity entity : w.getNearbyEntities(origin.clone().add(dir.clone().multiply(effectiveRange / 2.0)), effectiveRange / 2.0 + radius, effectiveRange / 2.0 + radius, effectiveRange / 2.0 + radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(shooter)) continue;
            if (!isNearBeam(origin, dir, effectiveRange, radius + 0.65, target.getEyeLocation())) continue;
            target.damage(damage, shooter);
            target.setFireTicks(160);
            w.spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 22, 0.4, 0.5, 0.4, 0.06);

            if (target instanceof Player playerTarget && plugin.getConfig().getBoolean("abilities.the_veteran.beam_removes_player_abilities", true)) {
                if (plugin.getAbilityManager().hasAbility(playerTarget)) {
                    plugin.getAbilityManager().removeAndForget(playerTarget);
                    playerTarget.sendMessage(plugin.getLocaleManager().msg(
                            "veteran.hit_by", "source", shooter.getName()));
                    shooter.sendMessage(plugin.getLocaleManager().msg(
                            "veteran.hit_target", "target", playerTarget.getName()));
                }
            }
        }
    }

    private boolean isNearBeam(Location origin, Vector dir, double range, double radius, Location target) {
        Vector rel = target.toVector().subtract(origin.toVector());
        double along = rel.dot(dir);
        if (along < 0 || along > range) return false;
        Vector closest = origin.toVector().add(dir.clone().multiply(along));
        return closest.distanceSquared(target.toVector()) <= radius * radius;
    }

    private void affectBlocksAround(Location center, double radius, boolean breakBlocks,
                                    boolean igniteBlocks, boolean surfaceOnly, int blockHitsToBreak,
                                    int[] blockBudget, Map<Block, Integer> weakenedBlocks) {
        int r = Math.max(1, (int) Math.ceil(radius));
        for (int x = -r; x <= r && blockBudget[0] > 0; x++) {
            for (int y = -r; y <= r && blockBudget[0] > 0; y++) {
                for (int z = -r; z <= r && blockBudget[0] > 0; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distanceSquared(center) > radius * radius) continue;
                    Block block = loc.getBlock();
                    Material type = block.getType();

                    if (breakBlocks && !type.isAir() && isBreakableByVeteranBeam(type)) {
                        if (surfaceOnly && !isSurfaceBlock(block)) continue;

                        int hits = weakenedBlocks.merge(block, 1, Integer::sum);
                        block.getWorld().spawnParticle(Particle.LARGE_SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02);
                        block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 2, 0.16, 0.16, 0.16, 0.02);
                        if (hits >= blockHitsToBreak) {
                            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 0.55f, 1.7f);
                            block.setType(Material.AIR, false);
                            weakenedBlocks.remove(block);
                            blockBudget[0]--;
                        }
                        continue;
                    }

                    if (igniteBlocks && type.isAir() && block.getRelative(0, -1, 0).getType().isSolid()) {
                        block.setType(Material.FIRE, false);
                        blockBudget[0]--;
                    }
                }
            }
        }
    }

    private boolean isSurfaceBlock(Block block) {
        return isOpen(block.getRelative(1, 0, 0).getType())
                || isOpen(block.getRelative(-1, 0, 0).getType())
                || isOpen(block.getRelative(0, 1, 0).getType())
                || isOpen(block.getRelative(0, -1, 0).getType())
                || isOpen(block.getRelative(0, 0, 1).getType())
                || isOpen(block.getRelative(0, 0, -1).getType());
    }

    private boolean isOpen(Material type) {
        return type.isAir() || !type.isSolid() || type == Material.FIRE;
    }

    private boolean isBreakableByVeteranBeam(Material type) {
        String name = type.name();
        return !name.equals("BEDROCK")
                && !name.equals("BARRIER")
                && !name.equals("COMMAND_BLOCK")
                && !name.equals("CHAIN_COMMAND_BLOCK")
                && !name.equals("REPEATING_COMMAND_BLOCK")
                && !name.equals("STRUCTURE_BLOCK")
                && !name.equals("JIGSAW")
                && !name.equals("END_PORTAL")
                && !name.equals("END_PORTAL_FRAME")
                && !name.equals("NETHER_PORTAL")
                && !name.equals("VAULT")
                && !name.equals("TRIAL_SPAWNER");
    }
}
