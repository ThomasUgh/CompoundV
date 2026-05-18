package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleporterAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TeleporterAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "teleporter"; }
    @Override public String getDisplayName() { return "Teleporter"; }
    @Override public int getColor() { return 0x9C27B0; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int resistance = plugin.getConfig().getInt("abilities.teleporter.resistance_level", 2);
        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, resistance - 1, false, false, true));
        }
    }

    @Override
    public void remove(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player player) {
        long cooldownMs = plugin.getConfig().getLong("abilities.teleporter.cooldown_ms", 2000L);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMs) {
            long seconds = Math.max(1, (cooldownMs - (now - last) + 999L) / 1000L);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("teleporter.cooldown",
                    "seconds", Long.toString(seconds)));
            return;
        }

        Location destination = findDestination(player);
        if (destination == null) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("teleporter.no_safe_location"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.75f);
            return;
        }

        Location from = player.getLocation();
        cooldowns.put(player.getUniqueId(), now);

        World world = player.getWorld();
        world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 72, 0.35, 0.65, 0.35, 0.45);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.25f);
        player.teleport(destination);
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 72, 0.35, 0.65, 0.35, 0.45);
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.45f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("teleporter.used"));
    }

    private Location findDestination(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = plugin.getConfig().getDouble("abilities.teleporter.range", 50.0);

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        Location raw;
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            BlockFace face = hit.getHitBlockFace();
            Block targetBlock = face == null ? block : block.getRelative(face);
            raw = targetBlock.getLocation().add(0.5, 0.0, 0.5);
        } else {
            raw = eye.clone().add(direction.multiply(range));
        }

        raw.setYaw(player.getLocation().getYaw());
        raw.setPitch(player.getLocation().getPitch());
        return nearestSafeLocation(raw);
    }

    private Location nearestSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;

        int x = origin.getBlockX();
        int y = origin.getBlockY();
        int z = origin.getBlockZ();

        for (int up = 0; up <= 4; up++) {
            Location safe = safeLocationAt(world, x, y + up, z, origin);
            if (safe != null) return safe;
        }

        for (int down = 1; down <= 12; down++) {
            Location safe = safeLocationAt(world, x, y - down, z, origin);
            if (safe != null) return safe;
        }

        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = 2; dy >= -8; dy--) {
                        Location safe = safeLocationAt(world, x + dx, y + dy, z + dz, origin);
                        if (safe != null) return safe;
                    }
                }
            }
        }

        return null;
    }

    private Location safeLocationAt(World world, int x, int y, int z, Location origin) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) return null;

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);

        if (!isPassable(feet.getType()) || !isPassable(head.getType())) return null;
        if (!below.getType().isSolid() || isDangerous(below.getType()) || isDangerous(feet.getType())) return null;

        Location safe = new Location(world, x + 0.5, y, z + 0.5, origin.getYaw(), origin.getPitch());
        return safe;
    }

    private boolean isPassable(Material material) {
        return material.isAir() || !material.isSolid();
    }

    private boolean isDangerous(Material material) {
        String name = material.name();
        return name.contains("LAVA")
                || name.equals("FIRE")
                || name.equals("SOUL_FIRE")
                || name.equals("MAGMA_BLOCK")
                || name.equals("CACTUS")
                || name.equals("POWDER_SNOW")
                || name.equals("CAMPFIRE")
                || name.equals("SOUL_CAMPFIRE");
    }
}
