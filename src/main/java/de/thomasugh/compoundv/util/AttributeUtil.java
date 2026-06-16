package de.thomasugh.compoundv.util;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AttributeUtil {

    private AttributeUtil() {
    }

    public static void setMaxHealthBonus(Player player, NamespacedKey key, double amount) {
        setAttributeModifier(player, resolveAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH"), key,
                amount, AttributeModifier.Operation.ADD_NUMBER);

        AttributeInstance instance = getAttribute(player, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (instance != null) {
            if (amount > 0) {
                player.setHealth(Math.min(instance.getValue(), player.getHealth() + amount));
            } else if (player.getHealth() > instance.getValue()) {
                player.setHealth(Math.max(1.0, instance.getValue()));
            }
        }
    }

    public static void setAttackSpeedBonus(Player player, NamespacedKey key, double amount) {
        setAttributeModifier(player, resolveAttribute("ATTACK_SPEED", "GENERIC_ATTACK_SPEED"), key,
                amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    public static void setScaleBonus(Player player, NamespacedKey key, double amount) {
        setAttributeModifier(player, resolveAttribute("SCALE", "GENERIC_SCALE"), key,
                amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    public static void setStepHeightBonus(Player player, NamespacedKey key, double amount) {
        setAttributeModifier(player,
                resolveAttribute("STEP_HEIGHT", "GENERIC_STEP_HEIGHT", "PLAYER_STEP_HEIGHT"), key,
                amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    private static void setAttributeModifier(Player player, Attribute attribute, NamespacedKey key,
                                             double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        removeModifier(instance, key);
        if (amount == 0) return;

        AttributeModifier modifier = createModifier(key, amount, operation);
        if (modifier != null) {
            instance.addModifier(modifier);
        }
    }

    private static AttributeInstance getAttribute(Player player, String... names) {
        Attribute attribute = resolveAttribute(names);
        return attribute == null ? null : player.getAttribute(attribute);
    }

    private static Attribute resolveAttribute(String... names) {
        for (String name : names) {
            Attribute attribute = valueOf(name);
            if (attribute != null) return attribute;
        }
        return null;
    }

    private static Attribute valueOf(String name) {
        try {
            return Attribute.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void removeModifier(AttributeInstance instance, NamespacedKey key) {
        instance.getModifiers().stream()
                .filter(modifier -> matches(modifier, key))
                .toList()
                .forEach(instance::removeModifier);
    }

    private static boolean matches(AttributeModifier modifier, NamespacedKey key) {
        try {
            Method getKey = modifier.getClass().getMethod("getKey");
            Object value = getKey.invoke(modifier);
            return key.equals(value);
        } catch (ReflectiveOperationException ignored) {
            return key.getKey().equals(modifier.getName());
        }
    }

    private static AttributeModifier createModifier(NamespacedKey key, double amount,
                                                    AttributeModifier.Operation operation) {
        try {
            Constructor<AttributeModifier> modern = AttributeModifier.class.getConstructor(
                    NamespacedKey.class, double.class, AttributeModifier.Operation.class);
            return modern.newInstance(key, amount, operation);
        } catch (ReflectiveOperationException ignored) {

        }

        try {
            Constructor<AttributeModifier> legacy = AttributeModifier.class.getConstructor(
                    UUID.class, String.class, double.class, AttributeModifier.Operation.class);
            return legacy.newInstance(UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)),
                    key.getKey(), amount, operation);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
