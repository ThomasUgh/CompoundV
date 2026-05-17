package de.thomasugh.compoundv.util;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public final class AttributeUtil {

    private AttributeUtil() {
    }

    public static void setMaxHealthBonus(Player player, NamespacedKey key, double amount) {
        Attribute attribute = resolveMaxHealthAttribute();
        if (attribute == null) return;

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        removeModifier(instance, key);

        if (amount <= 0) return;

        AttributeModifier modifier = createModifier(key, amount);
        if (modifier == null) return;

        instance.addModifier(modifier);
        player.setHealth(Math.min(instance.getValue(), player.getHealth() + amount));
    }

    private static Attribute resolveMaxHealthAttribute() {
        Attribute modern = valueOf("MAX_HEALTH");
        return modern != null ? modern : valueOf("GENERIC_MAX_HEALTH");
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

    private static AttributeModifier createModifier(NamespacedKey key, double amount) {
        AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_NUMBER;

        try {
            Constructor<AttributeModifier> modern = AttributeModifier.class.getConstructor(
                    NamespacedKey.class, double.class, AttributeModifier.Operation.class);
            return modern.newInstance(key, amount, operation);
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the legacy constructor.
        }

        try {
            Constructor<AttributeModifier> legacy = AttributeModifier.class.getConstructor(
                    UUID.class, String.class, double.class, AttributeModifier.Operation.class);
            return legacy.newInstance(UUID.nameUUIDFromBytes(key.toString().getBytes()), key.getKey(), amount, operation);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
