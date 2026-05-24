package de.thomasugh.compoundv.ability.shared;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

public abstract class BaseHeatVisionAbility implements Ability {

    protected final CompoundV plugin;
    private static final Map<UUID, Long> COOKED_BY_HEAT_VISION = new HashMap<>();

    protected final Map<UUID, Boolean> beamActive    = new HashMap<>();
    protected final Map<UUID, Integer> damageCounter = new HashMap<>();
    protected final Map<UUID, Integer> activeTicks   = new HashMap<>();
    protected final Map<UUID, Long>    cooldownUntil = new HashMap<>();

    protected BaseHeatVisionAbility(CompoundV plugin) { this.plugin = plugin; }

    @Override public boolean needsTick() { return true; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player p) {
        int strength = plugin.getConfig().getInt("abilities.heat_vision.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.heat_vision.resistance_level", 1);
        p.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void onToggle(Player p) {
        UUID uuid = p.getUniqueId();
        boolean current = beamActive.getOrDefault(uuid, false);

        if (!current) {
            long now = System.currentTimeMillis();
            long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
            if (readyAt > now) {
                long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
                MessageUtil.sendActionBar(p, heatVisionActionMessage("toggle.heat_vision_cooldown",
                        "seconds", Long.toString(seconds)));
                return;
            }

            beamActive.put(uuid, true);
            activeTicks.put(uuid, 0);
            damageCounter.remove(uuid);
            MessageUtil.sendActionBar(p, heatVisionActionMessage("toggle.heat_vision_on"));
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.6f);
            return;
        }

        beamActive.put(uuid, false);
        activeTicks.remove(uuid);
        damageCounter.remove(uuid);
        MessageUtil.sendActionBar(p, heatVisionActionMessage("toggle.heat_vision_off"));
    }

    @Override
    public void onTick(Player p) {
        UUID uuid = p.getUniqueId();
        if (!beamActive.getOrDefault(uuid, false)) return;

        int maxTicks = maxContinuousTicks();
        if (maxTicks > 0) {
            int ticks = activeTicks.merge(uuid, 1, Integer::sum);
            if (ticks >= maxTicks) {
                triggerOverheatCooldown(p);
                return;
            }
        }

        beam(p);
    }

    @Override
    public void remove(Player p) {
        UUID uuid = p.getUniqueId();
        beamActive.remove(uuid);
        damageCounter.remove(uuid);
        activeTicks.remove(uuid);
        cooldownUntil.remove(uuid);
        p.removePotionEffect(PotionEffects.STRENGTH);
        p.removePotionEffect(PotionEffects.RESISTANCE);
    }

    private void triggerOverheatCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long cooldownMs = overheatCooldownMs();
        beamActive.put(uuid, false);
        activeTicks.remove(uuid);
        damageCounter.remove(uuid);
        cooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));
        MessageUtil.sendActionBar(player, heatVisionActionMessage(
                "toggle.heat_vision_overheated",
                "seconds", Long.toString(Math.max(1L, (long) Math.ceil(cooldownMs / 1000.0)))));
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.45f, 1.4f);
    }

    private void beam(Player player) {

        double  range   = range();
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
        meltPassableSnowAlongBeam(player, lEye, dir, dL, w);
        meltPassableSnowAlongBeam(player, rEye, dir, dR, w);
        clearPassableVegetationAlongBeam(player, lEye, dir, dL, w);
        clearPassableVegetationAlongBeam(player, rEye, dir, dR, w);

        int cnt = damageCounter.merge(player.getUniqueId(), 1, Integer::sum);
        if (cnt % ivl != 0 || hit == null) return;

        if (hit.getHitEntity() instanceof LivingEntity le) {
            double finalDamage = adjustedDamageForTarget(le, damage);
            Location imp = le.getLocation().add(0, 1, 0);
            if (finalDamage <= 0.0) {
                w.spawnParticle(Particle.SMOKE, imp, 7, 0.16, 0.22, 0.16, 0.02);
                w.playSound(imp, Sound.BLOCK_ANVIL_LAND, 0.18f, 1.8f);
                return;
            }
            if (cooksMeatDrops()) markCookedByHeatVision(le);
            le.damage(finalDamage, player);
            if (igniteE) le.setFireTicks(entityFireTicks());
            w.spawnParticle(Particle.DUST,  imp, impactParticles(), 0.3, 0.4, 0.3, 0, core);
            w.spawnParticle(Particle.SMOKE, imp,  5, 0.1, 0.2, 0.1, 0.02);
            w.playSound(imp, hurtSoundFor(le), 0.8f, 1.0f);
        }

        if (hit.getHitBlock() != null) {
            Block    b   = hit.getHitBlock();
            Material mat = b.getType();
            Block standOn = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            if (b.equals(standOn) || b.equals(player.getLocation().getBlock())) return;
            if (tryMeltSnowOrIce(b, w)) {
                return;
            }
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
            if (isHeatVisionClearableVegetation(mat)) {
                clearVegetationBlock(b, w);
                return;
            }
            if (ignite && hit.getHitBlockFace() != null) {
                Block t = b.getRelative(hit.getHitBlockFace());
                if (t.getType() == Material.AIR) t.setType(Material.FIRE);
            }
        }
    }




    private void clearPassableVegetationAlongBeam(Player player, Location origin, Vector dir, double range, World world) {
        Block standOn = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        Block playerBlock = player.getLocation().getBlock();

        for (double d = 0.5; d <= range; d += 0.32) {
            Block block = origin.clone().add(dir.clone().multiply(d)).getBlock();
            if (block.equals(standOn) || block.equals(playerBlock)) continue;
            if (isHeatVisionClearableVegetation(block.getType())) {
                clearVegetationBlock(block, world);
            }
        }
    }

    private void clearVegetationBlock(Block block, World world) {
        Location loc = block.getLocation().add(0.5, 0.45, 0.5);
        world.spawnParticle(Particle.SMOKE, loc, 4, 0.12, 0.12, 0.12, 0.01);
        world.spawnParticle(Particle.FLAME, loc, 1, 0.08, 0.08, 0.08, 0.0);
        block.setType(Material.AIR);
    }

    private boolean isHeatVisionClearableVegetation(Material material) {
        String name = material.name();
        if (isProtectedFlowerLikeBlock(name)) {
            return false;
        }

        return name.equals("GRASS")
                || name.equals("SHORT_GRASS")
                || name.equals("TALL_GRASS")
                || name.equals("DRY_GRASS")
                || name.equals("SHORT_DRY_GRASS")
                || name.equals("TALL_DRY_GRASS")
                || name.equals("FERN")
                || name.equals("LARGE_FERN")
                || name.equals("BUSH")
                || name.equals("DEAD_BUSH")
                || name.equals("SWEET_BERRY_BUSH")
                || name.equals("FIREFLY_BUSH")
                || name.equals("LEAF_LITTER")
                || name.equals("MOSS_CARPET")
                || name.equals("PALE_MOSS_CARPET")
                || name.equals("PALE_HANGING_MOSS")
                || name.equals("SEAGRASS")
                || name.equals("TALL_SEAGRASS")
                || name.equals("KELP")
                || name.equals("KELP_PLANT")
                || name.equals("VINE")
                || name.equals("SMALL_DRIPLEAF")
                || name.equals("BIG_DRIPLEAF")
                || name.equals("BIG_DRIPLEAF_STEM")
                || name.equals("HANGING_ROOTS")
                || name.equals("NETHER_SPROUTS")
                || name.endsWith("_ROOTS")
                || name.endsWith("_SPROUTS")
                || name.contains("VINE")
                || name.endsWith("_BUSH");
    }

    private boolean isProtectedFlowerLikeBlock(String name) {
        return name.contains("FLOWER")
                || name.contains("TULIP")
                || name.contains("ORCHID")
                || name.equals("POPPY")
                || name.equals("DANDELION")
                || name.equals("ALLIUM")
                || name.equals("AZURE_BLUET")
                || name.equals("OXEYE_DAISY")
                || name.equals("CORNFLOWER")
                || name.equals("LILY_OF_THE_VALLEY")
                || name.equals("LILAC")
                || name.equals("PEONY")
                || name.equals("SUNFLOWER")
                || name.equals("ROSE_BUSH")
                || name.equals("PINK_PETALS")
                || name.equals("WILDFLOWERS")
                || name.equals("TORCHFLOWER")
                || name.equals("PITCHER_PLANT")
                || name.equals("SPORE_BLOSSOM")
                || name.endsWith("_PETALS");
    }

    private void meltPassableSnowAlongBeam(Player player, Location origin, Vector dir, double range, World world) {
        Block standOn = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        Block playerBlock = player.getLocation().getBlock();

        for (double d = 0.5; d <= range; d += 0.35) {
            Block block = origin.clone().add(dir.clone().multiply(d)).getBlock();
            if (block.equals(standOn) || block.equals(playerBlock)) continue;
            if (block.getType() == Material.SNOW) {
                tryMeltSnowOrIce(block, world);
                return;
            }
        }
    }

    private boolean tryMeltSnowOrIce(Block block, World world) {
        Material type = block.getType();
        String name = type.name();
        boolean flatSnow = type == Material.SNOW;
        boolean meltToWater = name.equals("SNOW_BLOCK")
                || name.equals("POWDER_SNOW")
                || name.equals("ICE")
                || name.equals("FROSTED_ICE")
                || name.equals("PACKED_ICE")
                || name.equals("BLUE_ICE");

        if (!flatSnow && !meltToWater) {
            return false;
        }

        Location meltLoc = block.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.CLOUD, meltLoc, 10, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticle(Particle.SMOKE, meltLoc, 6, 0.15, 0.15, 0.15, 0.01);
        world.playSound(meltLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.55f, 1.6f);
        block.setType(flatSnow ? Material.AIR : Material.WATER);
        return true;
    }

    private Sound hurtSoundFor(LivingEntity target) {
        String entityName = target.getType().name();

        try {
            return Sound.valueOf("ENTITY_" + entityName + "_HURT");
        } catch (IllegalArgumentException ignored) {
        }

        return switch (entityName) {
            case "PLAYER" -> Sound.ENTITY_PLAYER_HURT;
            case "COW", "MOOSHROOM" -> Sound.ENTITY_COW_HURT;
            case "PIG" -> Sound.ENTITY_PIG_HURT;
            case "SHEEP" -> Sound.ENTITY_SHEEP_HURT;
            case "CHICKEN" -> Sound.ENTITY_CHICKEN_HURT;
            case "WOLF" -> Sound.ENTITY_WOLF_HURT;
            case "CAT" -> Sound.ENTITY_CAT_HURT;
            case "VILLAGER" -> Sound.ENTITY_VILLAGER_HURT;
            case "IRON_GOLEM" -> Sound.ENTITY_IRON_GOLEM_HURT;
            case "ZOMBIE" -> Sound.ENTITY_ZOMBIE_HURT;
            case "SKELETON" -> Sound.ENTITY_SKELETON_HURT;
            case "CREEPER" -> Sound.ENTITY_CREEPER_HURT;
            case "SPIDER", "CAVE_SPIDER" -> Sound.ENTITY_SPIDER_HURT;
            case "ENDERMAN" -> Sound.ENTITY_ENDERMAN_HURT;
            case "BLAZE" -> Sound.ENTITY_BLAZE_HURT;
            case "WITHER" -> Sound.ENTITY_WITHER_HURT;
            case "ENDER_DRAGON" -> Sound.ENTITY_ENDER_DRAGON_HURT;
            default -> Sound.ENTITY_GENERIC_HURT;
        };
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


    protected int maxContinuousTicks() {
        return plugin.getConfig().getInt("heat_vision.max_continuous_ticks", 400);
    }

    protected long overheatCooldownMs() {
        return plugin.getConfig().getLong("heat_vision.overheat_cooldown_ms", 10000L);
    }

    protected String actionBarColorCode() {
        return "&b";
    }

    protected boolean boldActionBarLabel() {
        return false;
    }

    protected String actionBarLabel() {
        return getDisplayName();
    }

    protected String coloredActionBarLabel() {
        return actionBarColorCode()
                + (boldActionBarLabel() ? "&l" : "")
                + actionBarLabel()
                + "&r";
    }

    private String heatVisionActionMessage(String key, String... placeholders) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("color", actionBarColorCode() + (boldActionBarLabel() ? "&l" : ""));
        values.put("label", coloredActionBarLabel());
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            values.put(placeholders[i], placeholders[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', plugin.getLocaleManager().msg(key, values));
    }

    protected double range()        { return plugin.getConfig().getDouble("heat_vision.range", 43); }
    protected double damageAmount() { return plugin.getConfig().getDouble("heat_vision.damage_amount", 2.0); }
    protected boolean cooksMeatDrops() { return false; }

    protected double adjustedDamageForTarget(LivingEntity target, double damage) {
        if (target instanceof Player targetPlayer) {
            var ability = plugin.getAbilityManager().getAbility(targetPlayer);
            if (ability != null && ("the_ghost".equalsIgnoreCase(ability.getId())
                    || "invisibility".equalsIgnoreCase(ability.getId())
                    || "the_headpopper".equalsIgnoreCase(ability.getId()))) {
                return 0.0;
            }
            if (ability != null && "the_patriot_v_one".equalsIgnoreCase(ability.getId())) {
                return damage * plugin.getConfig().getDouble("heat_vision.v_one_received_damage_multiplier", 0.5);
            }
        }
        return damage;
    }

    private static void markCookedByHeatVision(LivingEntity entity) {
        COOKED_BY_HEAT_VISION.put(entity.getUniqueId(), System.currentTimeMillis() + 15000L);
    }

    public static boolean shouldCookDrops(LivingEntity entity) {
        Long until = COOKED_BY_HEAT_VISION.remove(entity.getUniqueId());
        return until != null && until >= System.currentTimeMillis();
    }
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
