package de.thomasugh.compoundv.gui;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.locale.LocaleManager;
import de.thomasugh.compoundv.util.VOneAbilities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AbilityShowcaseGui {

    public static final int SIZE = 54;
    public static final int CONTENT_SLOTS = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final CompoundV plugin;

    public AbilityShowcaseGui(CompoundV plugin) {
        this.plugin = plugin;
    }

    public static final class ShowcaseHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private ShowcaseHolder(int page) {
            this.page = page;
        }

        public int page() {
            return page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player, int page) {
        LocaleManager loc = plugin.getLocaleManager();
        List<Ability> abilities = orderedAbilities();

        int totalPages = Math.max(1, (int) Math.ceil(abilities.size() / (double) CONTENT_SLOTS));
        int current = Math.max(0, Math.min(page, totalPages - 1));

        ShowcaseHolder holder = new ShowcaseHolder(current);
        Inventory inv = Bukkit.createInventory(holder, SIZE, loc.msg("showcase.title"));
        holder.setInventory(inv);

        int start = current * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS; i++) {
            int index = start + i;
            if (index >= abilities.size()) break;
            inv.setItem(i, abilityItem(abilities.get(index)));
        }

        ItemStack filler = navItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int slot = CONTENT_SLOTS; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }

        if (current > 0) {
            inv.setItem(SLOT_PREV, navItem(Material.ARROW, loc.msg("showcase.prev"), null));
        }
        if (current < totalPages - 1) {
            inv.setItem(SLOT_NEXT, navItem(Material.ARROW, loc.msg("showcase.next"), null));
        }

        Map<String, String> info = new HashMap<>();
        info.put("count", Integer.toString(abilities.size()));
        info.put("page", Integer.toString(current + 1));
        info.put("pages", Integer.toString(totalPages));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(loc.msg("showcase.info_page", info));
        infoLore.add(loc.msg("showcase.info_count", info));
        infoLore.add(loc.msg("showcase.info_close"));
        inv.setItem(SLOT_INFO, navItem(Material.NETHER_STAR, loc.msg("showcase.info_title"), infoLore));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.4f);
    }

    private List<Ability> orderedAbilities() {

        return new ArrayList<>(plugin.getRegistry().all());
    }

    private ItemStack abilityItem(Ability ability) {
        ItemStack item = new ItemStack(materialFor(ability.getId()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(colorPrefix(ability.getColor()) + ChatColor.BOLD + ability.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(categoryLine(ability.getId()));
        lore.add("");
        List<String> description = plugin.getLocaleManager().msgList(ability.getDescriptionKey());
        if (description.isEmpty()) {
            lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC
                    + plugin.getLocaleManager().msg("showcase.no_description"));
        } else {
            lore.addAll(description);
        }

        meta.setLore(lore);
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        } catch (Throwable ignored) {

        }
        item.setItemMeta(meta);
        return item;
    }

    private String categoryLine(String abilityId) {
        LocaleManager loc = plugin.getLocaleManager();
        if (VOneAbilities.isUpgrade(abilityId)) {
            return loc.msg("showcase.category_v_one_upgrade");
        }
        if (VOneAbilities.isPure(abilityId)) {
            return loc.msg("showcase.category_v_one");
        }
        return loc.msg("showcase.category_compound_v");
    }

    private ItemStack navItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String colorPrefix(int rgb) {
        try {
            return net.md_5.bungee.api.ChatColor.of(
                    String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF)).toString();
        } catch (Throwable ignored) {
            return ChatColor.WHITE.toString();
        }
    }

    private Material materialFor(String abilityId) {
        String name = MATERIALS.getOrDefault(abilityId, "NETHER_STAR");
        Material material = Material.matchMaterial(name);
        return material != null ? material : Material.NETHER_STAR;
    }

    private static final Map<String, String> MATERIALS = buildMaterials();

    private static Map<String, String> buildMaterials() {
        Map<String, String> map = new HashMap<>();
        map.put("the_patriot", "FIRE_CHARGE");
        map.put("the_patriot_v_one", "BLAZE_POWDER");
        map.put("bloodweaver", "REDSTONE");
        map.put("bloodweaver_v_one", "REDSTONE_BLOCK");
        map.put("fly", "ELYTRA");
        map.put("heat_vision", "LIGHT_BLUE_DYE");
        map.put("heat_vision_2", "LIME_DYE");
        map.put("heat_vision_3", "ORANGE_DYE");
        map.put("heat_vision_4", "PURPLE_DYE");
        map.put("heat_vision_5", "RED_DYE");
        map.put("speedster", "SUGAR");
        map.put("strength", "IRON_INGOT");
        map.put("the_ghost", "PHANTOM_MEMBRANE");
        map.put("fire", "FLINT_AND_STEEL");
        map.put("the_diver", "HEART_OF_THE_SEA");
        map.put("the_runner", "RABBIT_FOOT");
        map.put("jumper", "SLIME_BALL");
        map.put("shockwave", "PISTON");
        map.put("vision", "ENDER_EYE");
        map.put("teleporter", "ENDER_PEARL");
        map.put("teleporter_v_one", "CHORUS_FRUIT");
        map.put("the_worm", "STRING");
        map.put("flash_light", "GLOWSTONE_DUST");
        map.put("fire_sonic", "MAGMA_CREAM");
        map.put("toxic_cloud", "FERMENTED_SPIDER_EYE");
        map.put("the_countess", "SPIDER_EYE");
        map.put("the_warrior", "NETHERITE_SWORD");
        map.put("the_headpopper", "SKELETON_SKULL");
        map.put("spider_weaver", "COBWEB");
        map.put("the_detonator", "TNT");
        map.put("ice_cube", "PACKED_ICE");
        map.put("size_changer", "AMETHYST_SHARD");
        map.put("size_changer_v_one", "AMETHYST_CLUSTER");
        map.put("the_veteran", "NETHERITE_INGOT");
        map.put("sonic_boom", "ECHO_SHARD");
        map.put("stormstrike", "LIGHTNING_ROD");
        map.put("heal_angel", "GOLDEN_APPLE");
        map.put("submarine", "NAUTILUS_SHELL");
        return map;
    }
}
