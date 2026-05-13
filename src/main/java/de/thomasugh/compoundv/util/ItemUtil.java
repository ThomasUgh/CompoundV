package de.thomasugh.compoundv.util;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.locale.LocaleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {

    private ItemUtil() {}

    public static ItemStack createBottle(CompoundVPlugin plugin, CompoundPotion type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setBasePotionType(PotionType.WATER);
        meta.setColor(type.getPotionColor());

        LocaleManager loc = plugin.getLocaleManager();
        String bundleKey = "bottle." + type.name().toLowerCase();

        Component name = loc.msg(bundleKey + ".name")
                .decoration(TextDecoration.ITALIC, false);
        List<Component> lore = new ArrayList<>();
        lore.add(loc.msg(bundleKey + ".lore_1").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(loc.msg(bundleKey + ".lore_2").decoration(TextDecoration.ITALIC, false));

        meta.displayName(name);
        meta.lore(lore);

        meta.getPersistentDataContainer()
                .set(CompoundVPlugin.BOTTLE_KEY, PersistentDataType.STRING, type.name());

        try {
            meta.setEnchantmentGlintOverride(true);
        } catch (NoSuchMethodError | NoSuchFieldError ignored) {

            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    public static CompoundPotion getBottleType(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer()
                .get(CompoundVPlugin.BOTTLE_KEY, PersistentDataType.STRING);
        if (v == null) return null;
        try { return CompoundPotion.valueOf(v); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static boolean isCompoundVBottle(ItemStack item) {
        return getBottleType(item) != null;
    }
}
