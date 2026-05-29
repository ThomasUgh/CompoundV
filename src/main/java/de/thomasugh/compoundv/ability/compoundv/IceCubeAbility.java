package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.AbilityKillTracker;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IceCubeAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> iceSpikes = new HashSet<>();
    private final Map<UUID, Long> spikeCooldown = new HashMap<>();
    private final Map<UUID, Long> wallCooldown = new HashMap<>();
    private final Map<String, Long> frostedBlocks = new HashMap<>();
    private final Map<String, TemporaryWallBlock> temporaryWallBlocks = new HashMap<>();

    public IceCubeAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "ice_cube"; }
    @Override public String getDisplayName() { return "IceCube"; }
    @Override public int getColor() { return 0xA7E8FF; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        applyPassiveEffects(player);
    }

    @Override
    public void remove(Player player) {
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.SPEED);
        spikeCooldown.remove(player.getUniqueId());
        wallCooldown.remove(player.getUniqueId());
    }

    @Override
    public void onTick(Player player) {
        applyPassiveEffects(player);
        freezeWaterAround(player);
        applyIceSpeed(player);
        applySlideAssist(player);
    }

    public void shootIceSpike(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("abilities.ice_cube.spike_cooldown_ms", 3000L);
        long readyAt = spikeCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("ice_cube.spike_cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 1.55f);
            return;
        }

        spikeCooldown.put(uuid, now + Math.max(0L, cooldownMs));
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.setVelocity(direction.clone().multiply(plugin.getConfig().getDouble("abilities.ice_cube.spike_velocity", 2.15)));
        iceSpikes.add(snowball.getUniqueId());

        World world = player.getWorld();
        world.playSound(eye, Sound.BLOCK_GLASS_BREAK, 0.65f, 1.85f);
        world.playSound(eye, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.7f);
        world.spawnParticle(Particle.SNOWFLAKE, eye.clone().add(direction.clone().multiply(0.6)), 18, 0.10, 0.10, 0.10, 0.04);
        world.spawnParticle(Particle.DUST, eye.clone().add(direction.clone().multiply(0.65)), 10, 0.06, 0.06, 0.06, 0,
                new Particle.DustOptions(Color.fromRGB(165, 235, 255), 0.85f));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("ice_cube.spike_fired"));
    }

    public boolean handleProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!iceSpikes.remove(projectile.getUniqueId())) return false;

        Location impact = projectile.getLocation();
        World world = projectile.getWorld();
        world.playSound(impact, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.25f);
        world.spawnParticle(Particle.SNOWFLAKE, impact, 32, 0.28, 0.28, 0.28, 0.06);
        world.spawnParticle(Particle.DUST, impact, 18, 0.18, 0.18, 0.18, 0,
                new Particle.DustOptions(Color.fromRGB(175, 240, 255), 1.0f));

        Entity hit = event.getHitEntity();
        if (!(hit instanceof LivingEntity target)) return true;
        if (projectile.getShooter() instanceof Player shooter && target != shooter) {
            applySpikeDebuffs(target);
            double damage = plugin.getConfig().getDouble("abilities.ice_cube.spike_damage_hearts", 0.0) * 2.0;
            if (damage > 0.0) {
                AbilityKillTracker.damage(plugin, target, shooter, damage, "death_messages.ice_cube", true);
            }
        }
        return true;
    }

    public void createIceWall(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("abilities.ice_cube.wall_cooldown_ms", 60000L);
        long readyAt = wallCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("ice_cube.wall_cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 1.25f);
            return;
        }

        int width = Math.max(1, plugin.getConfig().getInt("abilities.ice_cube.wall_width", 5));
        int height = Math.max(1, plugin.getConfig().getInt("abilities.ice_cube.wall_height", 3));
        double distance = Math.max(1.0, plugin.getConfig().getDouble("abilities.ice_cube.wall_distance", 4.0));
        long durationTicks = Math.max(20L, plugin.getConfig().getLong("abilities.ice_cube.wall_duration_ticks", 500L));

        Vector forward = player.getLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location center = player.getLocation().clone().add(forward.clone().multiply(distance));
        World world = center.getWorld();
        if (world == null) return;

        int placed = 0;
        int half = width / 2;
        long expiresAt = now + durationTicks * 50L;
        for (int x = -half; x <= half; x++) {
            for (int y = 0; y < height; y++) {
                Location target = center.clone().add(right.clone().multiply(x)).add(0, y, 0);
                Block block = target.getBlock();
                if (!isWallReplaceable(block.getType())) continue;
                if (block.getLocation().distanceSquared(player.getLocation()) < 1.25) continue;

                String key = blockKey(block.getLocation());
                if (temporaryWallBlocks.containsKey(key)) continue;

                BlockData original = block.getBlockData();
                Material wallMaterial = wallMaterialFor(x, y, half, height);
                block.setType(wallMaterial, false);
                temporaryWallBlocks.put(key, new TemporaryWallBlock(block.getLocation(), original, wallMaterial, expiresAt));
                placed++;
                SchedulerAdapter.runLaterAt(plugin, block.getLocation(), () -> expireWallBlock(key, expiresAt), durationTicks);
            }
        }

        if (placed <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.85f);
            return;
        }

        wallCooldown.put(uuid, now + Math.max(0L, cooldownMs));
        Location effect = center.clone().add(0, Math.max(1.0, height / 2.0), 0);
        world.playSound(effect, Sound.BLOCK_GLASS_PLACE, 1.0f, 0.75f);
        world.playSound(effect, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.25f);
        world.spawnParticle(Particle.SNOWFLAKE, effect, 55, width * 0.35, height * 0.25, 0.22, 0.08);
        world.spawnParticle(Particle.CLOUD, effect, 22, width * 0.22, height * 0.18, 0.14, 0.025);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("ice_cube.wall_created"));
    }

    private Material wallMaterialFor(int x, int y, int halfWidth, int height) {
        if (y == 0 || Math.abs(x) >= halfWidth) return Material.PACKED_ICE;
        if (y == height - 1 && Math.abs(x) <= 1) return Material.SNOW_BLOCK;
        if ((x + y) % 5 == 0) return Material.BLUE_ICE;
        return Material.ICE;
    }

    private boolean isWallReplaceable(Material material) {
        String name = material.name();
        return material.isAir()
                || material == Material.WATER
                || material == Material.SNOW
                || material == Material.POWDER_SNOW
                || name.equals("GRASS")
                || name.equals("SHORT_GRASS")
                || name.equals("TALL_GRASS")
                || name.equals("FERN")
                || name.equals("LARGE_FERN")
                || name.equals("VINE")
                || name.endsWith("_VINES")
                || name.endsWith("_CARPET");
    }

    private void expireWallBlock(String key, long expiresAt) {
        TemporaryWallBlock temporary = temporaryWallBlocks.get(key);
        if (temporary == null || temporary.expiresAt() != expiresAt) return;
        Block block = temporary.location().getBlock();
        if (block.getType() == temporary.wallMaterial()) {
            block.setBlockData(temporary.originalData(), false);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 4, 0.12, 0.12, 0.12, 0.01);
        }
        temporaryWallBlocks.remove(key);
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        if (target == null || target == attacker) return;
        int ticks = plugin.getConfig().getInt("abilities.ice_cube.melee_slowness_ticks", 100);
        int amplifier = Math.max(0, plugin.getConfig().getInt("abilities.ice_cube.melee_slowness_level", 1) - 1);
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, ticks, amplifier, false, true, true));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1.0, 0), 8, 0.22, 0.35, 0.22, 0.02);
    }

    private void applyPassiveEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, 80, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, 80, 0, false, false, true));
    }

    private void applySpikeDebuffs(LivingEntity target) {
        int slownessTicks = plugin.getConfig().getInt("abilities.ice_cube.spike_slowness_ticks", 100);
        int slownessAmplifier = Math.max(0, plugin.getConfig().getInt("abilities.ice_cube.spike_slowness_level", 3) - 1);
        int weaknessTicks = plugin.getConfig().getInt("abilities.ice_cube.spike_weakness_ticks", 200);
        int weaknessAmplifier = Math.max(0, plugin.getConfig().getInt("abilities.ice_cube.spike_weakness_level", 2) - 1);
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, slownessTicks, slownessAmplifier, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffects.WEAKNESS, weaknessTicks, weaknessAmplifier, false, true, true));
    }

    private void applyIceSpeed(Player player) {
        Material below = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType();
        if (!isIceLike(below)) return;
        player.addPotionEffect(new PotionEffect(PotionEffects.SPEED, 40, 1, false, false, true));
    }

    private void applySlideAssist(Player player) {
        if (!plugin.getConfig().getBoolean("abilities.ice_cube.slide.enabled", true)) return;
        if (!player.isOnGround() || player.isInsideVehicle() || player.isInWater()) return;
        if (player.isSneaking() && plugin.getConfig().getBoolean("abilities.ice_cube.slide.sneak_brakes", true)) return;

        Vector velocity = player.getVelocity();
        Vector horizontal = velocity.clone();
        horizontal.setY(0.0);
        if (horizontal.lengthSquared() < 0.0016) return;

        double maxSpeed = plugin.getConfig().getDouble("abilities.ice_cube.slide.max_horizontal_speed", 0.64);
        double multiplier = plugin.getConfig().getDouble("abilities.ice_cube.slide.multiplier", 1.045);
        Vector boosted = horizontal.multiply(multiplier);
        if (boosted.length() > maxSpeed) {
            boosted.normalize().multiply(maxSpeed);
        }
        player.setVelocity(new Vector(boosted.getX(), velocity.getY(), boosted.getZ()));
    }

    private void freezeWaterAround(Player player) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        int radius = Math.max(1, plugin.getConfig().getInt("abilities.ice_cube.frost_walker_radius", 2));
        int y = base.getBlockY() - 1;
        for (int x = base.getBlockX() - radius; x <= base.getBlockX() + radius; x++) {
            for (int z = base.getBlockZ() - radius; z <= base.getBlockZ() + radius; z++) {
                double dx = x + 0.5 - base.getX();
                double dz = z + 0.5 - base.getZ();
                if (dx * dx + dz * dz > radius * radius + 0.75) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.WATER) {
                    frostBlock(block);
                }
            }
        }
    }

    private void frostBlock(Block block) {
        Location location = block.getLocation();
        String key = blockKey(location);
        long now = System.currentTimeMillis();
        long keepMs = Math.max(1000L, plugin.getConfig().getLong("abilities.ice_cube.frosted_ice_keep_ms", 4500L));
        long expiresAt = now + keepMs;
        Long old = frostedBlocks.get(key);
        if (old != null && old > now && block.getType() == Material.FROSTED_ICE) {
            frostedBlocks.put(key, expiresAt);
            return;
        }

        block.setType(Material.FROSTED_ICE, false);
        frostedBlocks.put(key, expiresAt);
        block.getWorld().spawnParticle(Particle.SNOWFLAKE, location.clone().add(0.5, 1.05, 0.5), 5, 0.22, 0.02, 0.22, 0.01);
        SchedulerAdapter.runLaterAt(plugin, location, () -> meltFrostedBlock(location, key), keepMs / 50L);
    }

    private void meltFrostedBlock(Location location, String key) {
        Long expiresAt = frostedBlocks.get(key);
        if (expiresAt != null && expiresAt > System.currentTimeMillis()) return;
        frostedBlocks.remove(key);
        Block block = location.getBlock();
        if (block.getType() == Material.FROSTED_ICE) {
            block.setType(Material.WATER, false);
            location.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0.5, 0.75, 0.5), 6, 0.18, 0.05, 0.18, 0.01);
        }
    }

    private String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private boolean isIceLike(Material material) {
        String name = material.name();
        return name.equals("ICE")
                || name.equals("PACKED_ICE")
                || name.equals("BLUE_ICE")
                || name.equals("FROSTED_ICE");
    }

    private record TemporaryWallBlock(Location location, BlockData originalData, Material wallMaterial, long expiresAt) { }
}
