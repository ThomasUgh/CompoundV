package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.shared.BaseHeatVisionAbility;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PatriotAscensionCinematic {

    private static final String BASE = "abilities.the_patriot_v_one.ascension_cinematic";

    private final CompoundV plugin;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public PatriotAscensionCinematic(CompoundV plugin) {
        this.plugin = plugin;
    }

    public boolean isLocked(UUID uuid) {
        return uuid != null && active.contains(uuid);
    }

    public void cancel(UUID uuid) {
        if (uuid != null) {
            active.remove(uuid);
        }
    }

    public void play(Player player) {
        if (player == null) return;
        if (!plugin.getConfig().getBoolean(BASE + ".enabled", true)) return;

        final UUID uuid = player.getUniqueId();
        if (!active.add(uuid)) return;

        final long chargeTicks = Math.max(0L, plugin.getConfig().getLong(BASE + ".charge_up_ticks", 40L));
        final long beamTicks = Math.max(20L, plugin.getConfig().getLong(BASE + ".beam_duration_ticks", 80L));
        final double height = Math.max(8.0, plugin.getConfig().getDouble(BASE + ".beam_height", 110.0));
        final long totalTicks = chargeTicks + beamTicks;

        final Location anchor = player.getLocation().clone();
        final float lockedYaw = anchor.getYaw();
        final float originalWalk = safeWalkSpeed(player);

        final BaseHeatVisionAbility heatVision = plugin.getRegistry().get("the_patriot_v_one")
                .filter(a -> a instanceof BaseHeatVisionAbility)
                .map(a -> (BaseHeatVisionAbility) a)
                .orElse(null);

        player.setSprinting(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        trySetWalkSpeed(player, 0f);
        applyFreezeEffects(player, totalTicks + 10L);
        forceLookUp(player, anchor, lockedYaw);

        player.getWorld().playSound(anchor, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.5f);
        player.getWorld().playSound(anchor, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 0.7f);

        final TaskHandle[] handleBox = new TaskHandle[1];
        final Runnable driver = new Runnable() {
            long t = 0L;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || !active.contains(uuid)) {
                    release(uuid, p, originalWalk);
                    cancelHandle(handleBox);
                    return;
                }

                forceLookUp(p, anchor, lockedYaw);
                p.setVelocity(new Vector(0, 0, 0));
                if (p.isFlying() || p.getAllowFlight()) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }

                if (t < chargeTicks) {
                    renderChargeUp(p, anchor, t, chargeTicks);
                } else {
                    renderBeam(p, heatVision, height, t - chargeTicks);
                }

                t++;
                if (t > totalTicks) {
                    finish(p, anchor, height);
                    release(uuid, p, originalWalk);
                    cancelHandle(handleBox);
                }
            }
        };

        handleBox[0] = SchedulerAdapter.runTimer(plugin, player, driver, 1L, 1L);
    }

    private void renderChargeUp(Player p, Location anchor, long t, long chargeTicks) {
        World w = p.getWorld();
        Location base = p.getLocation().add(0, 0.2, 0);
        double progress = chargeTicks <= 0 ? 1.0 : (double) t / (double) chargeTicks;
        int ring = 10 + (int) (progress * 14);
        double radius = 1.8 - progress * 1.3;
        for (int i = 0; i < ring; i++) {
            double ang = (Math.PI * 2 * i) / ring + t * 0.25;
            double x = Math.cos(ang) * radius;
            double z = Math.sin(ang) * radius;
            w.spawnParticle(Particle.DUST, base.clone().add(x, 0.1, z), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 90, 70), 1.2f));
        }
        if (t % 6L == 0L) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, p.getEyeLocation(), 6, 0.25, 0.25, 0.25, 0.02);
            w.playSound(anchor, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 0.4f + (float) progress);
        }
    }

    private void renderBeam(Player p, BaseHeatVisionAbility heatVision, double length, long beamTick) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();

        if (beamTick % 2L == 0L) {
            if (heatVision != null) {
                heatVision.renderUpwardCinematicBeam(p, length);
            } else {
                double ox = eye.getX(), oy = eye.getY(), oz = eye.getZ();
                Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(45, 210, 255), 1.2f);
                for (double d = 0.5; d <= length; d += 0.8) {
                    w.spawnParticle(Particle.DUST, ox, oy + d, oz, 1, 0.03, 0.0, 0.03, 0, core);
                }
            }
        }

        w.spawnParticle(Particle.FLAME, eye.getX(), eye.getY() + 0.4, eye.getZ(), 4, 0.12, 0.2, 0.12, 0.01);
        if (beamTick % 4L == 0L) {
            w.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.35f, 0.55f);
            w.playSound(eye, Sound.ITEM_FIRECHARGE_USE, 0.22f, 0.6f);
        }
    }

    private void finish(Player p, Location anchor, double height) {
        World w = p.getWorld();
        Location top = anchor.clone().add(0, Math.min(height, 24.0), 0);
        w.spawnParticle(Particle.EXPLOSION, top, 2, 0.4, 0.4, 0.4, 0);
        w.spawnParticle(Particle.END_ROD, p.getEyeLocation(), 30, 0.3, 0.6, 0.3, 0.12);
        w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.6f, 1.0f);
    }

    private void release(UUID uuid, Player p, float originalWalk) {
        active.remove(uuid);
        if (p == null) return;
        trySetWalkSpeed(p, originalWalk <= 0f ? 0.2f : originalWalk);
        p.removePotionEffect(PotionEffects.SLOWNESS);
        p.removePotionEffect(PotionEffects.JUMP_BOOST);
        p.removePotionEffect(PotionEffects.MINING_FATIGUE);
    }

    private void applyFreezeEffects(Player p, long durationTicks) {
        int ticks = (int) Math.min(Integer.MAX_VALUE, durationTicks);
        p.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, ticks, 6, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffects.JUMP_BOOST, ticks, 128, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffects.MINING_FATIGUE, ticks, 4, false, false, false));
    }

    private void forceLookUp(Player p, Location anchor, float lockedYaw) {
        Location look = new Location(p.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), lockedYaw, -90f);
        if (SchedulerAdapter.isFolia()) {
            try {
                p.getClass().getMethod("teleportAsync", Location.class).invoke(p, look);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            p.teleport(look);
        } catch (Throwable ignored) {
        }
    }

    private float safeWalkSpeed(Player p) {
        try {
            return p.getWalkSpeed();
        } catch (Throwable ignored) {
            return 0.2f;
        }
    }

    private void trySetWalkSpeed(Player p, float speed) {
        try {
            p.setWalkSpeed(Math.max(-1f, Math.min(1f, speed)));
        } catch (Throwable ignored) {
        }
    }

    private void cancelHandle(TaskHandle[] box) {
        if (box[0] != null) {
            box[0].cancel();
            box[0] = null;
        }
    }
}
