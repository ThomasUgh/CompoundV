package de.thomasugh.compoundv.util;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.SerumType;
import de.thomasugh.compoundv.locale.LocaleManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
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
        return createBottle(plugin, type, Material.POTION);
    }

    public static ItemStack createBottle(CompoundV plugin, CompoundPotion type, Material material) {
        ItemStack item = new ItemStack(isPotionMaterial(material) ? material : Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        setWaterPotion(meta);
        meta.setColor(type.getPotionColor());

        LocaleManager loc = plugin.getLocaleManager();
        String bundleKey = "bottle." + type.name().toLowerCase();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', loc.msg(bundleKey + ".name")));

        List<String> lore = new ArrayList<>();
        lore.add(loc.msg(bundleKey + ".lore_1"));
        lore.add("");
        lore.add(loc.msg(bundleKey + ".lore_2"));
        if (type == CompoundPotion.V_NULL) {
            lore.add("");
            lore.add(loc.msg(bundleKey + ".lore_3"));
        }
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(CompoundV.BOTTLE_KEY, PersistentDataType.STRING, type.name());
        applyGlint(meta);
        addItemFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(meta, "HIDE_ATTRIBUTES");
        addItemFlagIfPresent(meta, "HIDE_POTION_EFFECTS");

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createSerum(CompoundV plugin, SerumType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        setWaterPotion(meta);
        meta.setColor(type.getColor());

        LocaleManager loc = plugin.getLocaleManager();
        String bundleKey = "serum." + type.name().toLowerCase();
        String name = loc.msg(bundleKey + ".name");
        if (name.equals(bundleKey + ".name")) {
            name = "&b" + type.getDisplayName();
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> lore = new ArrayList<>();
        String lore1 = loc.msg(bundleKey + ".lore_1");
        if (!lore1.equals(bundleKey + ".lore_1")) {
            lore.add(lore1);
            String lore2 = loc.msg(bundleKey + ".lore_2");
            if (!lore2.equals(bundleKey + ".lore_2")) lore.add(lore2);
        } else {
            lore.add(loc.msg("serum.default_lore_1"));
            lore.add(loc.msg("serum.default_lore_2"));
        }
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(CompoundV.SERUM_KEY, PersistentDataType.STRING, type.name());
        applyGlint(meta);
        addItemFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(meta, "HIDE_ATTRIBUTES");
        addItemFlagIfPresent(meta, "HIDE_POTION_EFFECTS");

        item.setItemMeta(meta);
        return item;
    }




    public static ItemStack createVNullArrow(CompoundV plugin, int amount) {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, Math.max(1, amount));
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta == null) {
            return item;
        }

        if (rawMeta instanceof PotionMeta meta) {
            meta.setColor(CompoundPotion.V_NULL.getPotionColor());
        }

        LocaleManager loc = plugin.getLocaleManager();
        rawMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', loc.msg("v_null_arrow.name")));

        List<String> lore = new ArrayList<>();
        lore.add(loc.msg("v_null_arrow.lore_1"));
        lore.add(loc.msg("v_null_arrow.lore_2"));
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
        rawMeta.setLore(lore);

        rawMeta.getPersistentDataContainer().set(CompoundV.V_NULL_ARROW_KEY, PersistentDataType.STRING, "v_null");
        applyGlint(rawMeta);
        addItemFlagIfPresent(rawMeta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(rawMeta, "HIDE_ATTRIBUTES");
        addItemFlagIfPresent(rawMeta, "HIDE_POTION_EFFECTS");

        item.setItemMeta(rawMeta);
        return item;
    }

    public static boolean isVNullArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getType() != Material.TIPPED_ARROW && item.getType() != Material.ARROW) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(CompoundV.V_NULL_ARROW_KEY, PersistentDataType.STRING);
    }

    public static ItemStack createWaterPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        setWaterPotion(meta);
        addItemFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(meta, "HIDE_POTION_EFFECTS");
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createTutorialBook(CompoundV plugin) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        LocaleManager loc = plugin.getLocaleManager();
        meta.setTitle(ChatColor.stripColor(loc.msg("tutorial_book.title")));
        meta.setAuthor(ChatColor.stripColor(loc.msg("tutorial_book.author")));

        List<String> pages = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            List<String> lines = loc.msgList("tutorial_book.page_" + i);
            if (!lines.isEmpty()) {
                pages.add(String.join("\n", lines));
            }
        }
        if (pages.isEmpty()) {
            pages.add("Compound V Guide\n\nCraft all serums in a workbench with exactly two ingredients per step.");
        }
        meta.setPages(pages);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createAwkwardPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        setPotionType(meta, PotionType.AWKWARD);
        meta.getPersistentDataContainer().set(CompoundV.SERUM_KEY, PersistentDataType.STRING, "AWKWARD_POTION");
        addItemFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
        addItemFlagIfPresent(meta, "HIDE_POTION_EFFECTS");
        item.setItemMeta(meta);
        return item;
    }

    private static void setWaterPotion(PotionMeta meta) {
        setPotionType(meta, PotionType.WATER);
    }

    private static void setPotionType(PotionMeta meta, PotionType potionType) {
        try {
            Method method = meta.getClass().getMethod("setBasePotionType", PotionType.class);
            method.invoke(meta, potionType);
            return;
        } catch (Throwable ignored) {
            // Older APIs expose setBasePotionData instead.
        }
        try {
            Class<?> dataClass = Class.forName("org.bukkit.potion.PotionData");
            Object data = dataClass.getConstructor(PotionType.class).newInstance(potionType);
            Method method = meta.getClass().getMethod("setBasePotionData", dataClass);
            method.invoke(meta, data);
        } catch (Throwable ignored) {
            // Default POTION items are water on supported 1.20.x/1.21.x servers.
        }
    }

    public static boolean isWaterPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (readBottleType(item) != null || readSerumType(item) != null) return false;
        String serumMarker = item.getItemMeta().getPersistentDataContainer()
                .get(CompoundV.SERUM_KEY, PersistentDataType.STRING);
        if ("AWKWARD_POTION".equalsIgnoreCase(serumMarker)) return false;
        PotionType type = readBasePotionType(meta);
        return type == null || "WATER".equalsIgnoreCase(type.name());
    }

    public static boolean isAwkwardPotion(ItemStack item) {
        if (item == null || !isPotionMaterial(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (readBottleType(item) != null || readSerumType(item) != null) return false;
        String serumMarker = item.getItemMeta().getPersistentDataContainer()
                .get(CompoundV.SERUM_KEY, PersistentDataType.STRING);
        if ("AWKWARD_POTION".equalsIgnoreCase(serumMarker)) return true;
        PotionType type = readBasePotionType(meta);
        if (type != null && "AWKWARD".equalsIgnoreCase(type.name())) return true;

        // Extra fallback for API variants where the base potion type is not exposed correctly.
        // German clients render this as "Seltsamer Trank", but the server usually has no
        // translated display name. Checking the raw ItemStack string keeps older forks usable.
        String raw = item.toString().toUpperCase();
        return raw.contains("AWKWARD") || raw.contains("SELTSAMER");
    }

    private static PotionType readBasePotionType(PotionMeta meta) {
        try {
            Method method = meta.getClass().getMethod("getBasePotionType");
            Object value = method.invoke(meta);
            if (value instanceof PotionType potionType) return potionType;
        } catch (Throwable ignored) {
            // Older APIs expose getBasePotionData instead.
        }
        try {
            Method method = meta.getClass().getMethod("getBasePotionData");
            Object data = method.invoke(meta);
            Method getType = data.getClass().getMethod("getType");
            Object value = getType.invoke(data);
            if (value instanceof PotionType potionType) return potionType;
        } catch (Throwable ignored) {
            // Unsupported API variant.
        }
        return null;
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

    public static SerumType getSerumType(ItemStack item) {
        if (item == null || !isPotionMaterial(item.getType())) {
            return null;
        }
        return readSerumType(item);
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
        if (value != null) {
            try {
                return CompoundPotion.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Fall through to display-name fallback for old items.
            }
        }

        if (item.getItemMeta().hasDisplayName()) {
            String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            for (CompoundPotion type : CompoundPotion.values()) {
                if (type.getDisplayName().equalsIgnoreCase(display)) return type;
            }
        }
        return null;
    }

    private static SerumType readSerumType(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(CompoundV.SERUM_KEY, PersistentDataType.STRING);
        if (value != null) {
            try {
                return SerumType.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // AWKWARD_POTION marker and older invalid values are handled elsewhere.
            }
        }

        if (item.getItemMeta().hasDisplayName()) {
            String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            for (SerumType type : SerumType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(display)) return type;
            }
        }
        return null;
    }

    public static boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }
}
