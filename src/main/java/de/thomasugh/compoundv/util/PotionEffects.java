package de.thomasugh.compoundv.util;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.Locale;

public final class PotionEffects {

    public static final PotionEffectType SPEED = resolve("SPEED");
    public static final PotionEffectType SLOWNESS = resolve("SLOW", "SLOWNESS");
    public static final PotionEffectType NAUSEA = resolve("CONFUSION", "NAUSEA");
    public static final PotionEffectType BLINDNESS = resolve("BLINDNESS");
    public static final PotionEffectType WEAKNESS = resolve("WEAKNESS");
    public static final PotionEffectType HUNGER = resolve("HUNGER");
    public static final PotionEffectType MINING_FATIGUE = resolve("SLOW_DIGGING", "MINING_FATIGUE");
    public static final PotionEffectType HASTE = resolve("FAST_DIGGING", "HASTE");
    public static final PotionEffectType POISON = resolve("POISON");
    public static final PotionEffectType WITHER = resolve("WITHER");
    public static final PotionEffectType STRENGTH = resolve("INCREASE_DAMAGE", "STRENGTH");
    public static final PotionEffectType RESISTANCE = resolve("DAMAGE_RESISTANCE", "RESISTANCE");
    public static final PotionEffectType FIRE_RESISTANCE = resolve("FIRE_RESISTANCE");
    public static final PotionEffectType REGENERATION = resolve("REGENERATION", "REGEN");
    public static final PotionEffectType NIGHT_VISION = resolve("NIGHT_VISION");
    public static final PotionEffectType GLOWING = resolve("GLOWING");
    public static final PotionEffectType INVISIBILITY = resolve("INVISIBILITY");
    public static final PotionEffectType WATER_BREATHING = resolve("WATER_BREATHING");
    public static final PotionEffectType DOLPHINS_GRACE = resolve("DOLPHINS_GRACE");
    public static final PotionEffectType CONDUIT_POWER = resolve("CONDUIT_POWER");
    public static final PotionEffectType JUMP_BOOST = resolve("JUMP", "JUMP_BOOST");

    private PotionEffects() {
    }

    public static void add(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier,
                           boolean ambient, boolean particles, boolean icon) {
        if (type == null) return;
        entity.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles, icon));
    }

    public static void remove(LivingEntity entity, PotionEffectType type) {
        if (type != null) {
            entity.removePotionEffect(type);
        }
    }

    private static PotionEffectType resolve(String... names) {
        for (String name : names) {
            PotionEffectType type = resolveByLegacyName(name);
            if (type != null) return type;

            PotionEffectType lower = resolveByLegacyName(name.toLowerCase(Locale.ROOT));
            if (lower != null) return lower;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType resolveByLegacyName(String name) {
        try {
            Method getByName = PotionEffectType.class.getMethod("getByName", String.class);
            Object value = getByName.invoke(null, name);
            return value instanceof PotionEffectType type ? type : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
