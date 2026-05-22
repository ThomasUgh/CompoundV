package de.thomasugh.compoundv.ability.shared;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PrivateGlowUtil;
import org.bukkit.Bukkit;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import de.thomasugh.compoundv.util.AttributeUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
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
    private final int color;
    private final NamespacedKey healthModKey;
    private final Map<UUID, Boolean> glowActive     = new HashMap<>();
    private final Map<UUID, Integer> glowTicker     = new HashMap<>();
    private final Map<UUID, Set<UUID>> visibleTargets = new HashMap<>();
    private final Set<UUID>          launching      = new HashSet<>();
    private final Map<UUID, Long>    launchCooldown = new HashMap<>();
    private final Map<UUID, Long>    fallImpactCooldown = new HashMap<>();

    public ThePatriotAbility(CompoundV plugin, String id, String tierKey, int color) {
        super(plugin);
        this.id = id; this.tierKey = tierKey; this.color = color;
        this.healthModKey = new NamespacedKey(plugin, "patriot_hearts");
    }

    @Override public String    getId()          { return id; }
    @Override public String    getDisplayName() { return "The Patriot"; }
    @Override public int getColor()       { return color; }
    @Override protected float  size()           { return isVOne() ? 0.82f : 0.72f; }
    @Override protected float  glowSize()       { return isVOne() ? 0.55f : 0.45f; }
    @Override protected double step()           { return isVOne() ? 0.18 : 0.24;  }
    @Override protected int    coreParticles()  { return isVOne() ? 6 : 3; }
    @Override protected int    glowParticles()  { return isVOne() ? 4 : 2; }
    @Override protected int    impactParticles(){ return isVOne() ? 28 : 18; }
    @Override protected int    entityFireTicks(){ return isVOne() ? 100 : 80; }
    @Override protected Color  coreColor()      { return Color.fromRGB(255, 20, 8); }
    @Override protected Color  glowColor()      { return Color.fromRGB(255, 65, 35); }
    @Override protected boolean fireParticles() { return false; }
    @Override protected String actionBarColorCode() { return isVOne() ? "&4" : "&c"; }
    @Override protected boolean boldActionBarLabel() { return isVOne(); }
    @Override protected String actionBarLabel() { return "Heatvision"; }
    @Override protected String coloredActionBarLabel() {
        return actionBarColorCode() + (boldActionBarLabel() ? "&l" : "") + actionBarLabel() + "&r";
    }
    @Override protected int maxContinuousTicks() { return plugin.getConfig().getInt(t("heat_vision_max_continuous_ticks"), isVOne() ? 600 : 500); }
    @Override protected long overheatCooldownMs() { return plugin.getConfig().getLong(t("heat_vision_overheat_cooldown_ms"), 5000L); }
    @Override protected boolean cooksMeatDrops() { return plugin.getConfig().getBoolean(t("heat_vision_cooks_meat"), true); }

    @Override
    protected double range() {
        return plugin.getConfig().getDouble(t("heat_vision_range"), isVOne() ? 50.0 : 44.0);
    }

    @Override
    protected double damageAmount() {
        double hearts = plugin.getConfig().getDouble(t("heat_vision_damage_hearts"), isVOne() ? 4.725 : 4.5);
        double multiplier = plugin.getConfig().getDouble(t("heat_vision_damage_multiplier"), 1.0);
        return Math.max(0.0, hearts) * 2.0 * Math.max(0.0, multiplier);
    }

    private boolean isVOne() { return "v_one".equalsIgnoreCase(tierKey); }

    @Override public String getDescriptionKey() { return "ability." + id + ".description"; }

    private String t(String k) { return "abilities.the_patriot." + tierKey + "." + k; }
    private String s(String k) { return "abilities.the_patriot.shared." + k; }

    @Override
    public void apply(Player p) {
        p.setAllowFlight(true);

        int str   = plugin.getConfig().getInt(t("strength_level"),   3);
        int res   = plugin.getConfig().getInt(t("resistance_level"), isVOne() ? 3 : 2);
        int regen = plugin.getConfig().getInt(t("regen_level"),      isVOne() ? 3 : 1);
        boolean fireRes = plugin.getConfig().getBoolean(t("fire_resistance"), true);

        p.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, str - 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, res - 1, false, false, true));
        if (fireRes) {
            p.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE,
                    Integer.MAX_VALUE, 0, false, false, true));
        }
        if (regen > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION,
                    Integer.MAX_VALUE, regen - 1, false, false, true));
        }

        double extraHp = plugin.getConfig().getDouble(s("extra_hearts"), 10.0) * 2.0;
        setMaxHealthBonus(p, extraHp);
    }

    @Override
    public void remove(Player p) {
        super.remove(p);
        UUID u = p.getUniqueId();
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        p.setFlySpeed(0.1f);
        List.of(PotionEffects.STRENGTH, PotionEffects.RESISTANCE,
                PotionEffects.FIRE_RESISTANCE, PotionEffects.REGENERATION,
                PotionEffects.NIGHT_VISION).forEach(p::removePotionEffect);
        if (glowActive.getOrDefault(u, false)) clearGlow(p);
        glowActive.remove(u); glowTicker.remove(u);
        launching.remove(u); launchCooldown.remove(u); fallImpactCooldown.remove(u);
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
            p.addPotionEffect(new PotionEffect(PotionEffects.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.xray_on"));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        } else {
            clearGlow(p);
            p.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.xray_off"));
        }
    }

    public boolean isGlowActive(Player p) {
        return glowActive.getOrDefault(p.getUniqueId(), false);
    }

    private void refreshGlow(Player p) {
        double r = plugin.getConfig().getDouble(s("glow_radius"), 50);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 35, 25), 1.1f);
        Set<UUID> currentTargets = new HashSet<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (!(e instanceof LivingEntity le) || e == p) continue;
            currentTargets.add(le.getUniqueId());
            if (!PrivateGlowUtil.showGlowing(p, le, ChatColor.RED, "cv_red_glow")) {
                renderPrivateGlowOutline(p, le, red);
            }
        }
        clearStaleGlow(p, currentTargets);
        visibleTargets.put(p.getUniqueId(), currentTargets);
    }

    private void clearGlow(Player p) {
        Set<UUID> oldTargets = visibleTargets.remove(p.getUniqueId());
        if (oldTargets == null) return;
        for (UUID targetId : oldTargets) {
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) {
                PrivateGlowUtil.clearGlowing(p, living);
            }
        }
    }

    private void clearStaleGlow(Player player, Set<UUID> currentTargets) {
        Set<UUID> oldTargets = visibleTargets.get(player.getUniqueId());
        if (oldTargets == null) return;
        for (UUID targetId : oldTargets) {
            if (currentTargets.contains(targetId)) continue;
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) {
                PrivateGlowUtil.clearGlowing(player, living);
            }
        }
    }

    private void renderPrivateGlowOutline(Player viewer, LivingEntity target, Particle.DustOptions dust) {
        double eyeHeight = Math.max(0.8, target.getEyeHeight());
        org.bukkit.Location base = target.getLocation().add(0, Math.min(1.15, eyeHeight * 0.55), 0);
        viewer.spawnParticle(Particle.DUST, base, 10, 0.30, Math.min(0.75, eyeHeight * 0.38), 0.30, 0, dust);
        viewer.spawnParticle(Particle.FLAME, base, 2, 0.18, 0.26, 0.18, 0.01);
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
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("launch_cooldown",
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
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(u);
            if (p.isOnline()) {
                p.setAllowFlight(true); p.setFlying(true);
                p.setFlySpeed((float) spd);
            }
        }, peak);
    }

    public void triggerFallImpact(Player p) {
        triggerFallImpact(p, plugin.getConfig().getDouble(s("fall_impact_block_height"), 40.0));
    }

    public void triggerFallImpact(Player p, double fallenBlocks) {
        Location loc = p.getLocation();
        World w = p.getWorld();
        double blockHeight = plugin.getConfig().getDouble(s("fall_impact_block_height"),
                plugin.getConfig().getDouble(s("fall_impact_height"), 40.0));

        if (fallenBlocks < blockHeight) {
            triggerSoftFallImpact(p, loc, w);
            return;
        }

        if (isFallImpactCoolingDown(p)) {
            return;
        }
        startFallImpactCooldown(p);

        float power = (float) plugin.getConfig().getDouble(s("fall_impact_power"), 4.0);
        boolean blockDamage = plugin.getConfig().getBoolean(s("fall_impact_block_damage"), true);
        w.createExplosion(loc, power, false, blockDamage, p);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.2f, 0.45f);
        w.playSound(loc, Sound.BLOCK_STONE_BREAK, 1.3f, 0.4f);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0, .3, 0), 50, 1.6, .15, 1.6, .2);
        w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, .5, 0), 7, 1.2, 0, 1.2, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, .5, 0), 35, 1.3, .1, 1.3, .05);
        w.spawnParticle(Particle.DUST, loc.clone().add(0, .6, 0), 30, 1.3, .3, 1.3, 0,
                new Particle.DustOptions(Color.fromRGB(200, 10, 0), 1.1f));
        MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("fall_impact"));
    }

    private boolean isFallImpactCoolingDown(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = fallImpactCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt <= now) return false;
        long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fall_impact_cooldown",
                "seconds", Long.toString(seconds)));
        return true;
    }

    private void startFallImpactCooldown(Player player) {
        long cooldownMs = plugin.getConfig().getLong(s("fall_impact_cooldown_ms"), 60000L);
        fallImpactCooldown.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
    }

    private void triggerSoftFallImpact(Player p, Location loc, World w) {
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.9f);
        w.playSound(loc, Sound.BLOCK_STONE_HIT, 0.8f, 0.65f);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0, .18, 0), 18, .75, .08, .75, .08);
        w.spawnParticle(Particle.POOF, loc.clone().add(0, .28, 0), 10, .55, .05, .55, 0.02);
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, .25, 0), 8, .5, .05, .5, .02);
        MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("fall_impact"));
    }

    private void setMaxHealthBonus(Player p, double amount) {
        AttributeUtil.setMaxHealthBonus(p, healthModKey, amount);
    }
}
