package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.SerumType;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BrewingRecipeListener implements Listener {

    private final CompoundV plugin;
    private final Map<String, TaskHandle> activeBrews = new HashMap<>();
    private final List<CustomRecipe> recipes;

    public BrewingRecipeListener(CompoundV plugin) {
        this.plugin = plugin;
        this.recipes = List.of(
                CustomRecipe.awkward(Material.AMETHYST_SHARD, SerumType.CRYSTALLINE_SERUM),
                CustomRecipe.serum(SerumType.CRYSTALLINE_SERUM, Material.ECHO_SHARD, SerumType.RESONANT_V_SERUM),
                CustomRecipe.serum(SerumType.RESONANT_V_SERUM, Material.GLOWSTONE_DUST, SerumType.ACTIVATED_V_SERUM),
                CustomRecipe.serum(SerumType.ACTIVATED_V_SERUM, Material.SUGAR, SerumType.UNSTABLE_TEMP_V),
                CustomRecipe.serum(SerumType.UNSTABLE_TEMP_V, Material.REDSTONE, CompoundPotion.TEMP_V),
                CustomRecipe.serum(SerumType.ACTIVATED_V_SERUM, Material.DIAMOND, SerumType.UNSTABLE_V_SERUM),
                CustomRecipe.serum(SerumType.UNSTABLE_V_SERUM, Material.NETHERITE_SCRAP, CompoundPotion.COMPOUND_V),

                CustomRecipe.awkward(Material.DRAGON_BREATH, SerumType.DRACONIC_SERUM),
                CustomRecipe.serum(SerumType.DRACONIC_SERUM, Material.ECHO_SHARD, SerumType.ECHO_CHARGED_SERUM),
                CustomRecipe.serum(SerumType.ECHO_CHARGED_SERUM, Material.NETHERITE_SCRAP, SerumType.REINFORCED_SERUM),
                CustomRecipe.serum(SerumType.REINFORCED_SERUM, Material.FERMENTED_SPIDER_EYE, SerumType.UNSTABLE_V_ONE_SERUM),
                CustomRecipe.serum(SerumType.UNSTABLE_V_ONE_SERUM, Material.TOTEM_OF_UNDYING, CompoundPotion.V_ONE),

                CustomRecipe.awkward(Material.MILK_BUCKET, SerumType.CLEANSING_SERUM),
                CustomRecipe.serum(SerumType.CLEANSING_SERUM, Material.GHAST_TEAR, SerumType.RESTORATIVE_SERUM),
                CustomRecipe.serum(SerumType.RESTORATIVE_SERUM, Material.FERMENTED_SPIDER_EYE, CompoundPotion.ANTI_V),

                CustomRecipe.awkward(Material.WITHER_ROSE, SerumType.DECAY_SERUM),
                CustomRecipe.serum(SerumType.DECAY_SERUM, Material.FERMENTED_SPIDER_EYE, SerumType.CORRUPTED_SERUM),
                CustomRecipe.serum(SerumType.CORRUPTED_SERUM, Material.ECHO_SHARD, SerumType.RESONANT_PATHOGEN),
                CustomRecipe.serum(SerumType.RESONANT_PATHOGEN, Material.DRAGON_BREATH, SerumType.AIRBORNE_V_PATHOGEN),
                CustomRecipe.serum(SerumType.AIRBORNE_V_PATHOGEN, Material.NETHERITE_SCRAP, SerumType.STABILIZED_V_NULL_PATHOGEN),
                CustomRecipe.serum(SerumType.STABILIZED_V_NULL_PATHOGEN, Material.TOTEM_OF_UNDYING, CompoundPotion.V_NULL),
                CustomRecipe.bottle(CompoundPotion.V_NULL, Material.POTION, Material.GUNPOWDER, CompoundPotion.V_NULL, Material.SPLASH_POTION),
                CustomRecipe.bottle(CompoundPotion.V_NULL, Material.SPLASH_POTION, Material.DRAGON_BREATH, CompoundPotion.V_NULL, Material.LINGERING_POTION)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        scheduleIfBrewer(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        scheduleIfBrewer(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        scheduleIfBrewer(event.getSource());
        scheduleIfBrewer(event.getDestination());
    }

    private void scheduleIfBrewer(Inventory inventory) {
        if (!(inventory instanceof BrewerInventory brewer)) return;
        SchedulerAdapter.runLater(plugin, () -> tryStartBrew(brewer), 1L);
    }

    private void tryStartBrew(BrewerInventory inventory) {
        String key = key(inventory);
        if (key == null || activeBrews.containsKey(key)) return;

        ItemStack ingredient = inventory.getIngredient();
        if (ingredient == null || ingredient.getType() == Material.AIR) return;

        CustomRecipe recipe = findRecipe(inventory, ingredient);
        if (recipe == null) return;
        if (!hasMatchingBottle(inventory, recipe)) return;
        if (!consumeFuelIfRequired(inventory)) return;

        consumeIngredient(inventory, ingredient);
        setBrewingTime(inventory, plugin.getConfig().getInt("brewing.custom_brew_time_ticks", 400));
        playStart(inventory);

        TaskHandle handle = SchedulerAdapter.runLater(plugin, () -> finishBrew(inventory, key, recipe),
                Math.max(1L, plugin.getConfig().getLong("brewing.custom_brew_time_ticks", 400)));
        activeBrews.put(key, handle);
    }

    private CustomRecipe findRecipe(BrewerInventory inventory, ItemStack ingredient) {
        Material ingredientType = ingredient.getType();
        for (CustomRecipe recipe : recipes) {
            if (recipe.ingredient() != ingredientType) continue;
            if (hasMatchingBottle(inventory, recipe)) return recipe;
        }
        return null;
    }

    private boolean hasMatchingBottle(BrewerInventory inventory, CustomRecipe recipe) {
        for (int slot = 0; slot < 3; slot++) {
            if (recipe.matches(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private void finishBrew(BrewerInventory inventory, String key, CustomRecipe recipe) {
        activeBrews.remove(key);
        boolean changed = false;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!recipe.matches(current)) continue;
            inventory.setItem(slot, recipe.createResult(plugin));
            changed = true;
        }

        setBrewingTime(inventory, 0);
        if (changed) playFinish(inventory);
        SchedulerAdapter.runLater(plugin, () -> tryStartBrew(inventory), 2L);
    }

    private boolean consumeFuelIfRequired(BrewerInventory inventory) {
        if (!plugin.getConfig().getBoolean("brewing.require_blaze_powder", false)) return true;
        ItemStack fuel = inventory.getFuel();
        if (fuel == null || fuel.getType() != Material.BLAZE_POWDER || fuel.getAmount() <= 0) return false;
        fuel.setAmount(fuel.getAmount() - 1);
        inventory.setFuel(fuel.getAmount() <= 0 ? null : fuel);
        return true;
    }

    private void consumeIngredient(BrewerInventory inventory, ItemStack ingredient) {
        Material type = ingredient.getType();
        if (ingredient.getAmount() <= 1) {
            inventory.setIngredient(type == Material.MILK_BUCKET ? new ItemStack(Material.BUCKET) : null);
            return;
        }

        ingredient.setAmount(ingredient.getAmount() - 1);
        inventory.setIngredient(ingredient);
        if (type == Material.MILK_BUCKET) {
            Location location = location(inventory);
            if (location != null) location.getWorld().dropItemNaturally(location.add(0.5, 0.8, 0.5), new ItemStack(Material.BUCKET));
        }
    }

    private void playStart(BrewerInventory inventory) {
        Location location = location(inventory);
        if (location == null) return;
        location.getWorld().playSound(location, Sound.BLOCK_BREWING_STAND_BREW, 0.45f, 1.8f);
    }

    private void playFinish(BrewerInventory inventory) {
        Location location = location(inventory);
        if (location == null) return;
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
        location.getWorld().spawnParticle(Particle.WITCH, location.clone().add(0.5, 1.05, 0.5), 28, 0.35, 0.25, 0.35, 0.04);
    }

    private void setBrewingTime(BrewerInventory inventory, int ticks) {
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof BrewingStand stand)) return;
        try {
            stand.setBrewingTime(Math.max(0, ticks));
            stand.update(true, false);
        } catch (Throwable ignored) {
            // Some forks may not expose writable brewing time reliably.
        }
    }

    private String key(BrewerInventory inventory) {
        Location location = location(inventory);
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Location location(BrewerInventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BrewingStand stand) {
            return stand.getLocation();
        }
        return null;
    }

    private record CustomRecipe(SourceKind sourceKind, SerumType sourceSerum, CompoundPotion sourceBottle,
                                Material sourceMaterial, Material ingredient,
                                SerumType resultSerum, CompoundPotion resultBottle, Material resultMaterial) {

        static CustomRecipe awkward(Material ingredient, SerumType result) {
            return new CustomRecipe(SourceKind.AWKWARD, null, null, Material.POTION, ingredient, result, null, Material.POTION);
        }

        static CustomRecipe serum(SerumType source, Material ingredient, SerumType result) {
            return new CustomRecipe(SourceKind.SERUM, source, null, Material.POTION, ingredient, result, null, Material.POTION);
        }

        static CustomRecipe serum(SerumType source, Material ingredient, CompoundPotion result) {
            return new CustomRecipe(SourceKind.SERUM, source, null, Material.POTION, ingredient, null, result, Material.POTION);
        }

        static CustomRecipe bottle(CompoundPotion source, Material sourceMaterial, Material ingredient,
                                   CompoundPotion result, Material resultMaterial) {
            return new CustomRecipe(SourceKind.BOTTLE, null, source, sourceMaterial, ingredient, null, result, resultMaterial);
        }

        boolean matches(ItemStack item) {
            if (item == null || item.getType() != sourceMaterial) return false;
            return switch (sourceKind) {
                case AWKWARD -> ItemUtil.isAwkwardPotion(item);
                case SERUM -> Objects.equals(ItemUtil.getSerumType(item), sourceSerum);
                case BOTTLE -> Objects.equals(ItemUtil.getAnyBottleType(item), sourceBottle);
            };
        }

        ItemStack createResult(CompoundV plugin) {
            if (resultSerum != null) return ItemUtil.createSerum(plugin, resultSerum);
            return ItemUtil.createBottle(plugin, resultBottle, resultMaterial);
        }
    }

    private enum SourceKind {
        AWKWARD,
        SERUM,
        BOTTLE
    }
}
