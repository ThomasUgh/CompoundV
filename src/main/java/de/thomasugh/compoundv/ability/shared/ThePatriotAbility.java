package de.thomasugh.compoundv.ability.shared;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.util.AbilityKillTracker;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.TeleportUtil;
import org.bukkit.Bukkit;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import de.thomasugh.compoundv.util.AttributeUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ThePatriotAbility extends BaseHeatVisionAbility {

    private static final String RED_TEAM = "cv_red_glow";

    private final String id, tierKey;
    private final int color;
    private final NamespacedKey healthModKey;
    private final Map<UUID, Boolean> glowActive     = new HashMap<>();
    private final Map<UUID, Integer> glowTicker     = new HashMap<>();
    private final Map<UUID, Set<UUID>> visibleTargets = new HashMap<>();
    private final Set<UUID>          launching      = new HashSet<>();
    private final Map<UUID, Long>    launchCooldown = new HashMap<>();
    private final Map<UUID, Long>    speedDashCooldown = new HashMap<>();
    private final Map<UUID, Long>    fallImpactCooldown = new HashMap<>();

    public ThePatriotAbility(CompoundV plugin, String id, String tierKey, int color) {
        super(plugin);
        this.id = id; this.tierKey = tierKey; this.color = color;
        this.healthModKey = new NamespacedKey(plugin, "patriot_hearts");
    }

    @Override public String    getId()          { return id; }
    @Override public String    getDisplayName() { return "The Patriot"; }
    @Override public int getColor()       { return color; }
    @Override protected float  size()           { return (float) Math.max(0.01, patriotParticleDouble("core_size", super.size())); }
    @Override protected float  glowSize()       { return (float) Math.max(0.01, patriotParticleDouble("glow_size", super.glowSize())); }
    @Override protected double step()           { return Math.max(0.08, patriotParticleDouble("step", super.step()));  }
    @Override protected int    coreParticles()  { return Math.max(0, plugin.getConfig().getInt(t("heat_vision_core_particles"), 2)); }
    @Override protected int    glowParticles()  { return Math.max(0, plugin.getConfig().getInt(t("heat_vision_glow_particles"), 1)); }
    @Override protected int    impactParticles(){ return Math.max(0, plugin.getConfig().getInt(t("heat_vision_impact_particles"), isVOne() ? 19 : 17)); }
    @Override protected int    entityFireTicks(){ return isVOne() ? 100 : 80; }
    @Override protected double hitRadius()      { return Math.max(0.01, plugin.getConfig().getDouble(t("heat_vision_hit_radius"), 0.0525)); }
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
    @Override protected String killMessageKey() { return "death_messages.the_patriot_heat_vision"; }
    @Override protected String activeSoundConfigPath() { return t("heat_vision_active_sound"); }
    @Override protected String fallbackActiveSoundConfigPath() { return s("heat_vision_active_sound"); }

    private double patriotParticleDouble(String key, double fallback) {
        double global = plugin.getConfig().getDouble("heat_vision.particles." + key, fallback);

        // Same behavior as normal Heatvision: global particle values are the
        // master controls. Per-Patriot particle values only win when explicitly
        // enabled for the tier.
        if (plugin.getConfig().getBoolean(t("custom_heat_vision_particles"), false)) {
            return plugin.getConfig().getDouble(t("heat_vision_" + key), global);
        }
        return global;
    }

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
        int res   = plugin.getConfig().getInt(t("resistance_level"), isVOne() ? 4 : 3);
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
        launching.remove(u); launchCooldown.remove(u); speedDashCooldown.remove(u); fallImpactCooldown.remove(u);
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
        int cap = plugin.getPerformanceManager() != null
                ? plugin.getPerformanceManager().maxScanEntities() : Integer.MAX_VALUE;
        int scanned = 0;
        Set<UUID> currentTargets = new HashSet<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (!(e instanceof LivingEntity le) || e == p) continue;
            if (scanned++ >= cap) break;
            currentTargets.add(le.getUniqueId());
            applyRedGlow(le);
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
                removeRedGlow(living);
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
                removeRedGlow(living);
            }
        }
    }

    private String scoreboardEntry(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        return entity.getUniqueId().toString();
    }

    private void applyRedGlow(LivingEntity target) {
        addToRedTeam(target);
        if (!target.isGlowing()) target.setGlowing(true);
    }

    private void removeRedGlow(LivingEntity target) {
        removeFromRedTeam(target);
        if (target.isGlowing()) target.setGlowing(false);
    }

    @SuppressWarnings("deprecation")
    private void addToRedTeam(Entity entity) {
        Team team = redTeam();
        if (team == null) return;
        try {
            String entry = scoreboardEntry(entity);
            if (!team.hasEntry(entry)) team.addEntry(entry);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void removeFromRedTeam(Entity entity) {
        try {
            var manager = Bukkit.getScoreboardManager();
            if (manager == null) return;
            Team team = manager.getMainScoreboard().getTeam(RED_TEAM);
            if (team == null) return;
            String entry = scoreboardEntry(entity);
            if (team.hasEntry(entry)) team.removeEntry(entry);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private Team redTeam() {
        try {
            var manager = Bukkit.getScoreboardManager();
            if (manager == null) return null;
            var sb = manager.getMainScoreboard();
            Team t = sb.getTeam(RED_TEAM);
            if (t == null) t = sb.registerNewTeam(RED_TEAM);
            t.setColor(ChatColor.RED);
            return t;
        } catch (Throwable ignored) {
            return null;
        }
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
        playConfiguredSound(loc, s("launch_flap_sound.sound"), "entity.ender_dragon.flap",
                s("launch_flap_sound.volume"), 1.10f,
                s("launch_flap_sound.pitch"), 1.35f);
        w.spawnParticle(Particle.CLOUD,       loc.clone().add(0, .2, 0), 50, 1.2, .15, 1.2, .25);
        w.spawnParticle(Particle.POOF,        loc.clone().add(0, .4, 0), 28, 1.0, .15, 1.0,  0);
        w.spawnParticle(Particle.EXPLOSION,   loc.clone().add(0, .5, 0),  6,  .8, .05,  .8,  0);
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, .3, 0), 25, 1.0, .10, 1.0, .05);
        w.spawnParticle(Particle.DUST, loc.clone().add(0, .8, 0), 25, 1.1, .3, 1.1, 0,
                new Particle.DustOptions(Color.fromRGB(220, 10, 0), 1f));
        p.setFlying(false); p.setAllowFlight(false);

        double vel  = plugin.getConfig().getDouble(s("launch_velocity"), 3.85);
        Vector look = p.getLocation().getDirection();
        p.setVelocity(new Vector(look.getX() * .25, vel, look.getZ() * .25));

        int    peak = plugin.getConfig().getInt(s("launch_peak_ticks"), 28);
        double spd  = plugin.getConfig().getDouble(t("launch_fly_speed"),
                plugin.getConfig().getDouble(s("launch_fly_speed"), 0.375));
        SchedulerAdapter.runLater(plugin, p, () -> {
            launching.remove(u);
            if (p.isOnline()) {
                p.setAllowFlight(true); p.setFlying(true);
                p.setFlySpeed((float) spd);
            }
        }, peak);
    }

    public boolean trySpeedDash(Player player) {
        if (!plugin.getConfig().getBoolean(s("speed_dash.enabled"), true)) return false;
        if (plugin.getConfig().getBoolean(s("speed_dash.require_sneak"), true) && !player.isSneaking()) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong(s("speed_dash.cooldown_ms"), 5000L);
        long readyAt = speedDashCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("patriot.speed_dash_cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 1.45f);
            return true;
        }

        Location destination = findSpeedDashDestination(player);
        if (destination == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.85f);
            return true;
        }

        Location from = player.getLocation().clone();
        speedDashCooldown.put(uuid, now + Math.max(0L, cooldownMs));
        renderSpeedDashTrail(from, destination);
        damageSpeedDashTargets(player, from, destination);
        playSpeedDashFlap(player);
        TeleportUtil.teleportSafely(plugin, player, destination);
        SchedulerAdapter.runLater(plugin, player, () -> {
            if (!player.isOnline()) return;
            if (plugin.getConfig().getBoolean(s("speed_dash.sound.repeat_at_destination"), true)) {
                playSpeedDashFlap(player);
            }
            renderSpeedDashArrival(player.getLocation());
        }, 1L);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("patriot.speed_dash"));
        return true;
    }

    private void damageSpeedDashTargets(Player player, Location from, Location to) {
        if (!plugin.getConfig().getBoolean(s("speed_dash.damage.enabled"), true)) return;
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) return;

        Vector start = from.toVector().add(new Vector(0, 0.90, 0));
        Vector end = to.toVector().add(new Vector(0, 0.90, 0));
        Vector delta = end.clone().subtract(start);
        double length = delta.length();
        if (length < 0.25) return;

        double radius = Math.max(0.35, plugin.getConfig().getDouble(s("speed_dash.damage.radius"), 2.20));
        double pathStep = Math.max(0.20, plugin.getConfig().getDouble(s("speed_dash.damage.path_step"), 0.45));
        int maxTargets = Math.max(1, plugin.getConfig().getInt(s("speed_dash.damage.max_targets"), 8));
        double minHearts = Math.max(0.0, plugin.getConfig().getDouble(s("speed_dash.damage.min_hearts"), 1.0));
        double maxHearts = Math.max(minHearts, plugin.getConfig().getDouble(s("speed_dash.damage.max_hearts"), 2.0));

        Vector mid = start.clone().add(end).multiply(0.5);
        Location searchCenter = new Location(world, mid.getX(), mid.getY(), mid.getZ());
        double searchX = Math.abs(end.getX() - start.getX()) * 0.5 + radius + 2.25;
        double searchY = Math.abs(end.getY() - start.getY()) * 0.5 + radius + 2.25;
        double searchZ = Math.abs(end.getZ() - start.getZ()) * 0.5 + radius + 2.25;

        Set<UUID> damaged = new HashSet<>();
        for (Entity entity : world.getNearbyEntities(searchCenter, searchX, searchY, searchZ)) {
            if (damaged.size() >= maxTargets) break;
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!target.isValid() || target.isDead()) continue;
            if (target instanceof Player playerTarget && (playerTarget.getGameMode() == org.bukkit.GameMode.CREATIVE || playerTarget.getGameMode() == org.bukkit.GameMode.SPECTATOR)) continue;
            if (!isNearSpeedDashPath(start, end, target, radius) && !didSpeedDashSampleHit(start, end, target, radius, pathStep)) continue;
            if (!damaged.add(target.getUniqueId())) continue;

            double hearts = minHearts + ThreadLocalRandom.current().nextDouble(maxHearts - minHearts + 0.0001);
            SchedulerAdapter.runLater(plugin, target, () -> {
                if (!player.isOnline() || !target.isValid() || target.isDead()) return;
                AbilityKillTracker.damage(plugin, target, player, hearts * 2.0, "death_messages.the_patriot_dash", true);
                Location hit = target.getLocation().add(0, Math.min(1.2, Math.max(0.7, target.getEyeHeight() * 0.65)), 0);
                target.getWorld().spawnParticle(Particle.CRIT, hit, 10, 0.28, 0.26, 0.28, 0.09);
                target.getWorld().spawnParticle(Particle.CLOUD, hit, 8, 0.30, 0.18, 0.30, 0.06);
                target.getWorld().playSound(hit, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.50f, 1.35f);
            }, 1L);
        }
    }

    private boolean isNearSpeedDashPath(Vector start, Vector end, LivingEntity target, double radius) {
        double radiusSquared = radius * radius;
        Location feet = target.getLocation();
        Vector base = feet.toVector().add(new Vector(0, 0.35, 0));
        Vector body = feet.toVector().add(new Vector(0, Math.min(1.45, Math.max(0.70, target.getEyeHeight() * 0.62)), 0));
        Vector eye = target.getEyeLocation().toVector();
        return distanceSquaredToSegment(base, start, end) <= radiusSquared
                || distanceSquaredToSegment(body, start, end) <= radiusSquared
                || distanceSquaredToSegment(eye, start, end) <= radiusSquared;
    }

    private boolean didSpeedDashSampleHit(Vector start, Vector end, LivingEntity target, double radius, double pathStep) {
        Vector delta = end.clone().subtract(start);
        double length = delta.length();
        if (length < 0.001) return false;

        int samples = Math.max(2, (int) Math.ceil(length / pathStep));
        double radiusSquared = radius * radius;
        org.bukkit.util.BoundingBox box = target.getBoundingBox().expand(radius, 0.75, radius);
        for (int i = 0; i <= samples; i++) {
            Vector point = start.clone().add(delta.clone().multiply(i / (double) samples));
            if (box.contains(point)
                    || box.contains(point.clone().add(new Vector(0, -0.45, 0)))
                    || box.contains(point.clone().add(new Vector(0, 0.45, 0)))) {
                return true;
            }
            if (target.getLocation().toVector().add(new Vector(0, 0.9, 0)).distanceSquared(point) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private double distanceSquaredToSegment(Vector point, Vector start, Vector end) {
        Vector segment = end.clone().subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 0.0001) return point.distanceSquared(start);
        double t = point.clone().subtract(start).dot(segment) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        Vector closest = start.clone().add(segment.multiply(t));
        return point.distanceSquared(closest);
    }

    private void playSpeedDashFlap(Player player) {
        if (!plugin.getConfig().getBoolean(s("speed_dash.sound.enabled"), true)) return;
        playConfiguredSound(player, s("speed_dash.sound.sound"), "entity.ender_dragon.flap",
                s("speed_dash.sound.volume"), 1.35f,
                s("speed_dash.sound.pitch"), 1.30f);
    }

    private void playConfiguredSound(Location location, String soundPath, String fallbackSound,
                                     String volumePath, float fallbackVolume,
                                     String pitchPath, float fallbackPitch) {
        String sound = plugin.getConfig().getString(soundPath, fallbackSound);
        if (sound == null || sound.isBlank()) return;
        location.getWorld().playSound(location, sound,
                Math.max(0.0f, (float) plugin.getConfig().getDouble(volumePath, fallbackVolume)),
                Math.max(0.01f, (float) plugin.getConfig().getDouble(pitchPath, fallbackPitch)));
    }

    private void playConfiguredSound(Player player, String soundPath, String fallbackSound,
                                     String volumePath, float fallbackVolume,
                                     String pitchPath, float fallbackPitch) {
        String sound = plugin.getConfig().getString(soundPath, fallbackSound);
        if (sound == null || sound.isBlank()) return;
        player.playSound(player.getLocation(), sound,
                Math.max(0.0f, (float) plugin.getConfig().getDouble(volumePath, fallbackVolume)),
                Math.max(0.01f, (float) plugin.getConfig().getDouble(pitchPath, fallbackPitch)));
    }

    private Location findSpeedDashDestination(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = Math.max(1.0, plugin.getConfig().getDouble(s("speed_dash.range"), 25.0));

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        double maxDistance = range;
        if (hit != null && hit.getHitPosition() != null) {
            maxDistance = Math.max(1.0, hit.getHitPosition().distance(eye.toVector()) - 0.75);
        }

        Location base = player.getLocation();
        for (double distance = maxDistance; distance >= 1.25; distance -= 0.75) {
            Location candidate = base.clone().add(direction.clone().multiply(distance));
            candidate.setYaw(base.getYaw());
            candidate.setPitch(base.getPitch());
            Location safe = nearestSafeSpeedDashLocation(candidate);
            if (safe != null && safe.distanceSquared(base) > 1.0) {
                return safe;
            }
        }
        return null;
    }

    private Location nearestSafeSpeedDashLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int x = origin.getBlockX();
        int y = origin.getBlockY();
        int z = origin.getBlockZ();

        for (int up = 0; up <= 3; up++) {
            Location safe = speedDashLocationAt(world, x, y + up, z, origin);
            if (safe != null) return safe;
        }
        for (int down = 1; down <= 6; down++) {
            Location safe = speedDashLocationAt(world, x, y - down, z, origin);
            if (safe != null) return safe;
        }
        return null;
    }

    private Location speedDashLocationAt(World world, int x, int y, int z, Location origin) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) return null;
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);

        if (!isSpeedDashPassable(feet.getType()) || !isSpeedDashPassable(head.getType())) return null;
        if (!below.getType().isSolid() || isSpeedDashDangerous(below.getType()) || isSpeedDashDangerous(feet.getType())) return null;

        return new Location(world, x + 0.5, y, z + 0.5, origin.getYaw(), origin.getPitch());
    }

    private boolean isSpeedDashPassable(Material material) {
        return material.isAir() || !material.isSolid();
    }

    private boolean isSpeedDashDangerous(Material material) {
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

    private void renderSpeedDashTrail(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) return;
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.1) return;
        Vector direction = delta.normalize();

        world.spawnParticle(Particle.CLOUD, from.clone().add(0, 0.45, 0), 26, 0.55, 0.16, 0.55, 0.12);
        world.spawnParticle(Particle.POOF, from.clone().add(0, 0.85, 0), 16, 0.40, 0.16, 0.40, 0.04);
        world.spawnParticle(Particle.SMOKE, from.clone().add(0, 0.65, 0), 8, 0.36, 0.10, 0.36, 0.025);

        for (double d = 0.0; d <= length; d += 0.42) {
            Location point = from.clone().add(direction.clone().multiply(d)).add(0, 0.95, 0);
            world.spawnParticle(Particle.POOF, point, 3, 0.13, 0.06, 0.13, 0.014);
            world.spawnParticle(Particle.CLOUD, point.clone().subtract(direction.clone().multiply(0.20)), 2, 0.18, 0.05, 0.18, 0.020);
            if (d % 1.26 < 0.42) {
                world.spawnParticle(Particle.SMOKE, point, 2, 0.12, 0.05, 0.12, 0.010);
            }
        }
    }

    private void renderSpeedDashArrival(Location destination) {
        World world = destination.getWorld();
        if (world == null) return;
        Location center = destination.clone().add(0, 0.55, 0);
        world.spawnParticle(Particle.CLOUD, center, 24, 0.52, 0.20, 0.52, 0.08);
        world.spawnParticle(Particle.POOF, center.clone().add(0, 0.35, 0), 14, 0.38, 0.14, 0.38, 0.035);
        world.spawnParticle(Particle.SMOKE, center, 8, 0.28, 0.10, 0.28, 0.018);
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
