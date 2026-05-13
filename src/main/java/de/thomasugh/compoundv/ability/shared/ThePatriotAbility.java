package de.thomasugh.compoundv.ability.shared;

import de.thomasugh.compoundv.CompoundVPlugin;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ThePatriotAbility extends BaseHeatVisionAbility {

    private final String id, tierKey;
    private final TextColor color;
    private final NamespacedKey healthModKey;
    private final Map<UUID, Boolean> glowActive     = new HashMap<>();
    private final Map<UUID, Integer> glowTicker     = new HashMap<>();
    private final Set<UUID>          launching      = new HashSet<>();
    private final Map<UUID, Long>    launchCooldown = new HashMap<>();

    public ThePatriotAbility(CompoundVPlugin plugin, String id, String tierKey, TextColor color) {
        super(plugin);
        this.id = id; this.tierKey = tierKey; this.color = color;
        this.healthModKey = new NamespacedKey(plugin, "patriot_hearts");
    }

    @Override public String    getId()          { return id; }
    @Override public String    getDisplayName() { return "The Patriot"; }
    @Override public TextColor getColor()       { return color; }
    @Override protected float  size()           { return isVOne() ? 0.82f : 0.72f; }
    @Override protected float  glowSize()       { return isVOne() ? 0.55f : 0.45f; }
    @Override protected double step()           { return isVOne() ? 0.18 : 0.24;  }
    @Override protected int    coreParticles()  { return isVOne() ? 5 : 3; }
    @Override protected int    glowParticles()  { return isVOne() ? 3 : 2; }
    @Override protected int    impactParticles(){ return isVOne() ? 28 : 18; }
    @Override protected int    entityFireTicks(){ return isVOne() ? 100 : 80; }
    @Override protected Color  coreColor()      { return Color.fromRGB(255, 20, 8); }
    @Override protected Color  glowColor()      { return Color.fromRGB(255, 65, 35); }
    @Override protected boolean fireParticles() { return false; }

    @Override
    protected double damageAmount() {
        return plugin.getConfig().getDouble(t("heat_vision_damage_amount"), isVOne() ? 10.0 : 8.0);
    }

    private boolean isVOne() { return "v_one".equalsIgnoreCase(tierKey); }

    @Override public String getDescriptionKey() { return "ability." + id + ".description"; }

    private String t(String k) { return "abilities.the_patriot." + tierKey + "." + k; }
    private String s(String k) { return "abilities.the_patriot.shared." + k; }

    @Override
    public void apply(Player p) {
        p.setAllowFlight(true);

        int str   = plugin.getConfig().getInt(t("strength_level"),   3);
        int res   = plugin.getConfig().getInt(t("resistance_level"), 2);
        int regen = plugin.getConfig().getInt(t("regen_level"),      1);
        boolean fireRes = plugin.getConfig().getBoolean(t("fire_resistance"), true);

        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, str - 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, res - 1, false, false, true));
        if (fireRes) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    Integer.MAX_VALUE, 0, false, false, true));
        }
        if (regen > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    Integer.MAX_VALUE, regen - 1, false, false, true));
        }

        double extraHp = plugin.getConfig().getDouble(s("extra_hearts"), 10.0) * 2.0;
        setMaxHealthBonus(p, extraHp);
    }

    @Override
    public void remove(Player p) {
        super.remove(p);
        UUID u = p.getUniqueId();
        p.setAllowFlight(false); p.setFlying(false); p.setFlySpeed(0.1f);
        List.of(PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.REGENERATION,
                PotionEffectType.NIGHT_VISION).forEach(p::removePotionEffect);
        if (glowActive.getOrDefault(u, false)) clearGlow(p);
        glowActive.remove(u); glowTicker.remove(u);
        launching.remove(u); launchCooldown.remove(u);
        setMaxHealthBonus(p, 0);
    }

    @Override
    public void onTick(Player p) {
        super.onTick(p);
        if (!glowActive.getOrDefault(p.getUniqueId(), false)) return;
        if (glowTicker.merge(p.getUniqueId(), 1, Integer::sum) % 20 == 0) refreshGlow(p);
    }

    public void toggleGlowRadar(Player p) {
        boolean next = !glowActive.getOrDefault(p.getUniqueId(), false);
        glowActive.put(p.getUniqueId(), next);
        if (next) {
            refreshGlow(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.xray_on"));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        } else {
            clearGlow(p);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.xray_off"));
        }
    }

    public boolean isGlowActive(Player p) {
        return glowActive.getOrDefault(p.getUniqueId(), false);
    }

    private void refreshGlow(Player p) {
        double r = plugin.getConfig().getDouble(s("glow_radius"), 50);
        Team   t = redTeam();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (!(e instanceof LivingEntity le) || e == p) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    40, 0, false, false, false));
            t.addEntry(e.getUniqueId().toString());
        }
    }

    private void clearGlow(Player p) {
        double r = plugin.getConfig().getDouble(s("glow_radius"), 50) + 20;
        Team   t = redTeam();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof LivingEntity le) le.removePotionEffect(PotionEffectType.GLOWING);
            t.removeEntry(e.getUniqueId().toString());
        }
    }

    @SuppressWarnings("deprecation")
    private Team redTeam() {
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("cv_red_glow");
        if (t == null) { t = sb.registerNewTeam("cv_red_glow"); t.setColor(ChatColor.RED); }
        return t;
    }

    public boolean isLaunching(Player p) { return launching.contains(p.getUniqueId()); }

    public void tryLaunch(Player p) {
        UUID u = p.getUniqueId();
        if (launching.contains(u) || p.isFlying()) return;
        long cd  = plugin.getConfig().getLong(s("launch_cooldown_ms"), 10000L);
        long now = System.currentTimeMillis();
        long lst = launchCooldown.getOrDefault(u, 0L);
        if (now - lst < cd) {
            long secs = (cd - (now - lst)) / 1000 + 1;
            p.sendActionBar(plugin.getLocaleManager().msg("launch_cooldown",
                    "seconds", Long.toString(secs)));
            return;
        }
        launching.add(u); launchCooldown.put(u, now);
        Location loc = p.getLocation(); World w = p.getWorld();
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,           2.0f, 0.5f);
        w.playSound(loc, Sound.ITEM_FIRECHARGE_USE,              1.5f, 0.6f);
        w.spawnParticle(Particle.CLOUD,       loc.clone().add(0, .2, 0), 50, 1.2, .15, 1.2, .25);
        w.spawnParticle(Particle.POOF,        loc.clone().add(0, .4, 0), 28, 1.0, .15, 1.0,  0);
        w.spawnParticle(Particle.EXPLOSION,   loc.clone().add(0, .5, 0),  6,  .8, .05,  .8,  0);
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, .3, 0), 25, 1.0, .10, 1.0, .05);
        w.spawnParticle(Particle.DUST, loc.clone().add(0, .8, 0), 25, 1.1, .3, 1.1, 0,
                new Particle.DustOptions(Color.fromRGB(220, 10, 0), 1f));
        p.setFlying(false); p.setAllowFlight(false);

        double vel  = plugin.getConfig().getDouble(s("launch_velocity"), 3.5);
        Vector look = p.getLocation().getDirection();
        p.setVelocity(new Vector(look.getX() * .25, vel, look.getZ() * .25));

        int    peak = plugin.getConfig().getInt(s("launch_peak_ticks"), 28);
        double spd  = plugin.getConfig().getDouble(t("launch_fly_speed"),
                plugin.getConfig().getDouble(s("launch_fly_speed"), 0.375));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            launching.remove(u);
            if (p.isOnline()) {
                p.setAllowFlight(true); p.setFlying(true);
                p.setFlySpeed((float) spd);
            }
        }, peak);
    }

    public void triggerFallImpact(Player p) {
        Location loc = p.getLocation(); World w = p.getWorld();
        float   power = (float) plugin.getConfig().getDouble(s("fall_impact_power"), 4.0);
        boolean bDmg  = plugin.getConfig().getBoolean(s("fall_impact_block_damage"), true);
        w.createExplosion(loc, power, false, bDmg, p);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.2f, 0.45f);
        w.playSound(loc, Sound.BLOCK_STONE_BREAK,      1.3f, 0.4f);
        w.spawnParticle(Particle.CLOUD,       loc.clone().add(0, .3, 0), 50, 1.6, .15, 1.6, .2);
        w.spawnParticle(Particle.EXPLOSION,   loc.clone().add(0, .5, 0),  7, 1.2,  0,  1.2,  0);
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, .5, 0), 35, 1.3, .1,  1.3, .05);
        w.spawnParticle(Particle.DUST, loc.clone().add(0, .6, 0), 30, 1.3, .3, 1.3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 10, 0), 1.1f));
        p.sendActionBar(plugin.getLocaleManager().msg("fall_impact"));
    }

    private void setMaxHealthBonus(Player p, double amount) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        attr.getModifiers().stream()
                .filter(m -> healthModKey.equals(m.getKey()))
                .toList()
                .forEach(attr::removeModifier);
        if (amount > 0) {
            attr.addModifier(new AttributeModifier(
                    healthModKey, amount, AttributeModifier.Operation.ADD_NUMBER));

            p.setHealth(Math.min(attr.getValue(), p.getHealth() + amount));
        }
    }
}
