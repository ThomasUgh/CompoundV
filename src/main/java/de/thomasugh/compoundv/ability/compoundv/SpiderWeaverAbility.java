package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpiderWeaverAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> webCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();

    public SpiderWeaverAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "spider_weaver"; }
    @Override public String getDisplayName() { return "SpiderWeaver"; }
    @Override public int getColor() { return 0xD7D7D7; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.spider_weaver.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.spider_weaver.resistance_level", 1);
        int regeneration = plugin.getConfig().getInt("abilities.spider_weaver.regeneration_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION, Integer.MAX_VALUE, Math.max(0, regeneration - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        webCooldown.remove(player.getUniqueId());
        lastHandledAt.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.REGENERATION);
    }

    @Override
    public void onTick(Player player) {
        if (!plugin.getConfig().getBoolean("abilities.spider_weaver.wall_climb_enabled", true)) return;
        if (player.isOnGround() || player.isFlying() || player.isGliding() || player.isSwimming()) return;
        if (!isNearWall(player)) return;

        double speed = plugin.getConfig().getDouble("abilities.spider_weaver.wall_climb_velocity", 0.19);
        Vector velocity = player.getVelocity();
        if (velocity.getY() < speed) {
            player.setVelocity(new Vector(velocity.getX() * 0.72, speed, velocity.getZ() * 0.72));
        }
        player.setFallDistance(0f);
        if (System.currentTimeMillis() % 6L == 0L) {
            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 0.8, 0), 2, 0.22, 0.35, 0.22, 0.02);
        }
    }

    @Override
    public void onToggle(Player player) {
        shootWeb(player);
    }

    public void shootWeb(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long now = System.currentTimeMillis();
        long readyAt = webCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("spider_weaver.cooldown", "seconds", Long.toString(seconds)));
            return;
        }
        webCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.spider_weaver.web_cooldown_ms", 5000L));

        double range = plugin.getConfig().getDouble("abilities.spider_weaver.web_range", 24.0);
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult result = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.45,
                entity -> entity != player && entity instanceof LivingEntity);
        double distance = result != null && result.getHitPosition() != null ? result.getHitPosition().distance(eye.toVector()) : range;
        renderWebLine(world, eye, dir, distance);
        world.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.65f, 1.65f);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            trapTarget(player, target);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("spider_weaver.hit"));
        }
    }

    private void trapTarget(Player player, LivingEntity target) {
        int slownessTicks = plugin.getConfig().getInt("abilities.spider_weaver.web_slowness_ticks", 120);
        int amplifier = plugin.getConfig().getInt("abilities.spider_weaver.web_slowness_amplifier", 5);
        double damage = plugin.getConfig().getDouble("abilities.spider_weaver.web_damage_hearts", 1.5) * 2.0;
        if (damage > 0) target.damage(damage, player);
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, slownessTicks, Math.max(0, amplifier), false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffects.WEAKNESS, slownessTicks, 0, false, true, true));
        placeTemporaryWebs(target.getLocation(), plugin.getConfig().getInt("abilities.spider_weaver.web_duration_ticks", 1200));
        target.getWorld().spawnParticle(Particle.WHITE_ASH, target.getLocation().add(0, 1, 0), 52, 0.68, 0.65, 0.68, 0.025);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.75f, 1.25f);
    }

    private void placeTemporaryWebs(Location center, int durationTicks) {
        Set<Block> placed = new HashSet<>();
        Block base = center.getBlock();
        Block[] candidates = new Block[] {
                base,
                base.getRelative(BlockFace.NORTH),
                base.getRelative(BlockFace.SOUTH),
                base.getRelative(BlockFace.EAST),
                base.getRelative(BlockFace.WEST),
                base.getRelative(BlockFace.NORTH).getRelative(BlockFace.EAST),
                base.getRelative(BlockFace.NORTH).getRelative(BlockFace.WEST),
                base.getRelative(BlockFace.SOUTH).getRelative(BlockFace.EAST),
                base.getRelative(BlockFace.SOUTH).getRelative(BlockFace.WEST),
                base.getRelative(BlockFace.UP),
                base.getRelative(BlockFace.UP).getRelative(BlockFace.NORTH),
                base.getRelative(BlockFace.UP).getRelative(BlockFace.SOUTH),
                base.getRelative(BlockFace.UP).getRelative(BlockFace.EAST),
                base.getRelative(BlockFace.UP).getRelative(BlockFace.WEST)
        };
        for (Block block : candidates) {
            if (block.getType() != Material.AIR && !block.isPassable()) continue;
            block.setType(Material.COBWEB, false);
            placed.add(block);
        }
        if (placed.isEmpty()) return;
        SchedulerAdapter.runLaterAt(plugin, base.getLocation(), () -> {
            for (Block block : placed) {
                if (block.getType() == Material.COBWEB) block.setType(Material.AIR, false);
            }
        }, Math.max(20, durationTicks));
    }

    private void renderWebLine(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(235, 235, 235), 0.85f);
        for (double d = 0.45; d <= distance; d += 0.24) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 2, 0.035, 0.035, 0.035, 0, white);
            if (((int) (d * 10)) % 5 == 0) world.spawnParticle(Particle.WHITE_ASH, point, 1, 0.03, 0.03, 0.03, 0.01);
        }
    }

    private boolean isNearWall(Player player) {
        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        for (BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            if (isClimbableSurface(feet.getRelative(face)) || isClimbableSurface(head.getRelative(face))) return true;
        }
        return false;
    }

    private boolean isClimbableSurface(Block block) {
        return block.getType().isSolid() && !block.isPassable();
    }
}
