package de.thomasugh.compoundv.ability.shared;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class BaseHeatVisionAbility implements Ability {

    protected final CompoundVPlugin plugin;
    protected final Map<UUID, Boolean> beamActive    = new HashMap<>();
    protected final Map<UUID, Integer> damageCounter = new HashMap<>();

    protected BaseHeatVisionAbility(CompoundVPlugin plugin) { this.plugin = plugin; }

    @Override public boolean needsTick() { return true; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player p) {
    }

    @Override
    public void onToggle(Player p) {
        boolean next = !beamActive.getOrDefault(p.getUniqueId(), false);
        beamActive.put(p.getUniqueId(), next);
        if (!next) damageCounter.remove(p.getUniqueId());
        p.sendActionBar(next
                ? plugin.getLocaleManager().msg("toggle.heat_vision_on")
                : plugin.getLocaleManager().msg("toggle.heat_vision_off"));
        if (next) p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.6f);
    }

    @Override
    public void onTick(Player p) {
        if (beamActive.getOrDefault(p.getUniqueId(), false)) beam(p);
    }

    @Override
    public void remove(Player p) {
        beamActive.remove(p.getUniqueId());
        damageCounter.remove(p.getUniqueId());
    }

    private void beam(Player player) {

        double  range   = plugin.getConfig().getDouble ("heat_vision.range",          43);
        double  damage  = damageAmount();
        int     ivl     = damageInterval();
        boolean ignite  = plugin.getConfig().getBoolean("heat_vision.ignite_blocks",   true);
        boolean igniteE = plugin.getConfig().getBoolean("heat_vision.ignite_entities", true);
        boolean bGlass  = plugin.getConfig().getBoolean("heat_vision.break_glass",     true);
        boolean bLeaves = plugin.getConfig().getBoolean("heat_vision.break_leaves",    true);

        Location eye = player.getEyeLocation();
        Vector   dir = eye.getDirection().normalize();
        World    w   = player.getWorld();

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vector(0.12, 0, 0);
        else right.normalize().multiply(0.12);

        Location lEye = eye.clone().subtract(right);
        Location rEye = eye.clone().add(right);
        RayTraceResult rL = w.rayTrace(lEye, dir, range, FluidCollisionMode.NEVER, true, 0.05,
                e -> e != player && e instanceof LivingEntity);
        RayTraceResult rR = w.rayTrace(rEye, dir, range, FluidCollisionMode.NEVER, true, 0.05,
                e -> e != player && e instanceof LivingEntity);
        RayTraceResult hit = rL != null ? rL : rR;
        double dL = rL != null ? rL.getHitPosition().distance(lEye.toVector()) : range;
        double dR = rR != null ? rR.getHitPosition().distance(rEye.toVector()) : range;

        Particle.DustOptions core = new Particle.DustOptions(coreColor(), size());
        Particle.DustOptions glow = new Particle.DustOptions(glowColor(), glowSize());
        strand(w, lEye, dir, dL, core, glow, fireParticles());
        strand(w, rEye, dir, dR, core, glow, fireParticles());

        int cnt = damageCounter.merge(player.getUniqueId(), 1, Integer::sum);
        if (cnt % ivl != 0 || hit == null) return;

        if (hit.getHitEntity() instanceof LivingEntity le) {
            le.damage(damage, player);
            if (igniteE) le.setFireTicks(entityFireTicks());
            Location imp = le.getLocation().add(0, 1, 0);
            w.spawnParticle(Particle.DUST,  imp, impactParticles(), 0.3, 0.4, 0.3, 0, core);
            w.spawnParticle(Particle.SMOKE, imp,  5, 0.1, 0.2, 0.1, 0.02);
            w.playSound(imp, Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        }

        if (hit.getHitBlock() != null) {
            Block    b   = hit.getHitBlock();
            Material mat = b.getType();
            Block standOn = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            if (b.equals(standOn) || b.equals(player.getLocation().getBlock())) return;
            if (bGlass && mat.name().contains("GLASS")) {
                w.playSound(b.getLocation().add(0.5,0.5,0.5), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
                b.setType(Material.AIR); return;
            }
            if (bLeaves && mat.name().contains("LEAVES")) {
                b.setType(Material.AIR);
                for (int i = 1; i <= 2; i++) {
                    Block next = b.getLocation().add(dir.clone().multiply(i)).getBlock();
                    if (next.getType().name().contains("LEAVES")) next.setType(Material.AIR); else break;
                }
                return;
            }
            if (ignite && hit.getHitBlockFace() != null) {
                Block t = b.getRelative(hit.getHitBlockFace());
                if (t.getType() == Material.AIR) t.setType(Material.FIRE);
            }
        }
    }

    private void strand(World w, Location origin, Vector dir, double dist,
                        Particle.DustOptions core, Particle.DustOptions glow,
                        boolean fireParticles) {
        for (double d = 0.5; d <= dist; d += step()) {
            Location pos = origin.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.DUST, pos, coreParticles(), 0.01, 0.01, 0.01, 0, core);
            if (d % 0.9 < step()) w.spawnParticle(Particle.DUST, pos, glowParticles(), 0.005, 0.005, 0.005, 0, glow);
            if (fireParticles && d % 1.4 < step())
                w.spawnParticle(Particle.FLAME, pos, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    protected double damageAmount() { return plugin.getConfig().getDouble("heat_vision.damage_amount", 2.0); }
    protected int damageInterval()  { return plugin.getConfig().getInt("heat_vision.damage_interval", 2); }
    protected Color coreColor()     { return Color.fromRGB(45, 210, 255); }
    protected Color glowColor()     { return Color.fromRGB(150, 235, 255); }
    protected boolean fireParticles() { return plugin.getConfig().getBoolean("heat_vision.fire_particles", false); }
    protected int coreParticles()   { return 2; }
    protected int glowParticles()   { return 1; }
    protected int impactParticles() { return 12; }
    protected int entityFireTicks() { return 50; }
    protected float glowSize()      { return 0.35f; }
    protected float  size()         { return 0.55f; }
    protected double step()         { return 0.40; }

    public boolean isBeamActive(Player p) {
        return beamActive.getOrDefault(p.getUniqueId(), false);
    }
}
