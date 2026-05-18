package de.thomasugh.compoundv.util;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.locale.LocaleManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {

    private ItemUtil() {
    }

    public static ItemStack createBottle(CompoundV plugin, CompoundPotion type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        setWaterPotion(meta);
        meta.setColor(type.getPotionColor());

        LocaleManager loc = plugin.getLocaleManager();
        String bundleKey = "bottle." + type.name().toLowerCase();

        meta.setDisplayName(loc.msg(bundleKey + ".name"));

        List<String> lore = new ArrayList<>();
        lore.add(loc.msg(bundleKey + ".lore_1"));
        lore.add("");
        lore.add(loc.msg(bundleKey + ".lore_2"));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(CompoundV.BOTTLE_KEY, PersistentDataType.STRING, type.name());
        applyGlint(meta);
        addItemFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(meta, "HIDE_ATTRIBUTES");

        item.setItemMeta(meta);
        return item;
    }

    private static void setWaterPotion(PotionMeta meta) {
        try {
            Method method = meta.getClass().getMethod("setBasePotionType", PotionType.class);
            method.invoke(meta, PotionType.WATER);
        } catch (Throwable ignored) {
            // Default POTION items are water on supported 1.20.x/1.21.x servers.
        }
    }

    private static void applyGlint(ItemMeta meta) {
        try {
            Method method = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
            method.invoke(meta, Boolean.TRUE);
            return;
        } catch (Throwable ignored) {
            // Older Spigot versions do not expose setEnchantmentGlintOverride.
        }

        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        addItemFlagIfPresent(meta, "HIDE_ENCHANTS");
    }

    private static void addItemFlagIfPresent(ItemMeta meta, String flagName) {
        try {
            meta.addItemFlags(ItemFlag.valueOf(flagName));
        } catch (IllegalArgumentException ignored) {
            // ItemFlag does not exist on this server version.
        }
    }

    public static CompoundPotion getBottleType(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return null;
        }
        return readBottleType(item);
    }

    public static CompoundPotion getAnyBottleType(ItemStack item) {
        if (item == null || !isPotionMaterial(item.getType())) {
            return null;
        }
        return readBottleType(item);
    }

    public static boolean isCompoundVBottle(ItemStack item) {
        return getBottleType(item) != null;
    }

    public static boolean isThrowableCompoundVBottle(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type != Material.SPLASH_POTION && type != Material.LINGERING_POTION) return false;
        return getAnyBottleType(item) != null;
    }

    private static CompoundPotion readBottleType(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(CompoundV.BOTTLE_KEY, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return CompoundPotion.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }
}
