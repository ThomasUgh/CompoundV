package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.SerumType;
import de.thomasugh.compoundv.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Objects;

public class WorkbenchRecipeListener implements Listener {

    private final CompoundV plugin;
    private final List<CustomRecipe> recipes;

    public WorkbenchRecipeListener(CompoundV plugin) {
        this.plugin = plugin;
        this.recipes = List.of(
                CustomRecipe.water("awkward_from_water", Material.NETHER_WART),

                CustomRecipe.awkward("crystalline_serum", Material.AMETHYST_SHARD, SerumType.CRYSTALLINE_SERUM),
                CustomRecipe.serum("resonant_v_serum", SerumType.CRYSTALLINE_SERUM, Material.ECHO_SHARD, SerumType.RESONANT_V_SERUM),
                CustomRecipe.serum("activated_v_serum", SerumType.RESONANT_V_SERUM, Material.GLOWSTONE_DUST, SerumType.ACTIVATED_V_SERUM),
                CustomRecipe.serum("unstable_temp_v", SerumType.ACTIVATED_V_SERUM, Material.SUGAR, SerumType.UNSTABLE_TEMP_V),
                CustomRecipe.serum("temp_v", SerumType.UNSTABLE_TEMP_V, Material.REDSTONE, CompoundPotion.TEMP_V),
                CustomRecipe.serum("unstable_v_serum", SerumType.ACTIVATED_V_SERUM, Material.DIAMOND, SerumType.UNSTABLE_V_SERUM),
                CustomRecipe.serum("compound_v", SerumType.UNSTABLE_V_SERUM, Material.NETHERITE_SCRAP, CompoundPotion.COMPOUND_V),

                CustomRecipe.awkward("draconic_serum", Material.DRAGON_BREATH, SerumType.DRACONIC_SERUM),
                CustomRecipe.serum("echo_charged_serum", SerumType.DRACONIC_SERUM, Material.ECHO_SHARD, SerumType.ECHO_CHARGED_SERUM),
                CustomRecipe.serum("reinforced_serum", SerumType.ECHO_CHARGED_SERUM, Material.NETHERITE_SCRAP, SerumType.REINFORCED_SERUM),
                CustomRecipe.serum("unstable_v_one_serum", SerumType.REINFORCED_SERUM, Material.FERMENTED_SPIDER_EYE, SerumType.UNSTABLE_V_ONE_SERUM),
                CustomRecipe.serum("v_one", SerumType.UNSTABLE_V_ONE_SERUM, Material.TOTEM_OF_UNDYING, CompoundPotion.V_ONE),

                CustomRecipe.awkward("cleansing_serum", Material.MILK_BUCKET, SerumType.CLEANSING_SERUM),
                CustomRecipe.serum("restorative_serum", SerumType.CLEANSING_SERUM, Material.GHAST_TEAR, SerumType.RESTORATIVE_SERUM),
                CustomRecipe.serum("anti_v", SerumType.RESTORATIVE_SERUM, Material.FERMENTED_SPIDER_EYE, CompoundPotion.ANTI_V),

                CustomRecipe.awkward("decay_serum", Material.WITHER_ROSE, SerumType.DECAY_SERUM),
                CustomRecipe.serum("corrupted_serum", SerumType.DECAY_SERUM, Material.FERMENTED_SPIDER_EYE, SerumType.CORRUPTED_SERUM),
                CustomRecipe.serum("resonant_pathogen", SerumType.CORRUPTED_SERUM, Material.ECHO_SHARD, SerumType.RESONANT_PATHOGEN),
                CustomRecipe.serum("airborne_v_pathogen", SerumType.RESONANT_PATHOGEN, Material.DRAGON_BREATH, SerumType.AIRBORNE_V_PATHOGEN),
                CustomRecipe.serum("stabilized_v_null_pathogen", SerumType.AIRBORNE_V_PATHOGEN, Material.NETHERITE_SCRAP, SerumType.STABILIZED_V_NULL_PATHOGEN),
                CustomRecipe.serum("v_null", SerumType.STABILIZED_V_NULL_PATHOGEN, Material.TOTEM_OF_UNDYING, CompoundPotion.V_NULL),
                CustomRecipe.bottle("v_null_splash", CompoundPotion.V_NULL, Material.POTION, Material.GUNPOWDER, CompoundPotion.V_NULL, Material.SPLASH_POTION),
                CustomRecipe.bottle("v_null_lingering", CompoundPotion.V_NULL, Material.SPLASH_POTION, Material.DRAGON_BREATH, CompoundPotion.V_NULL, Material.LINGERING_POTION)
        );
        cleanupNativeWorkbenchRecipes();
    }

    private void cleanupNativeWorkbenchRecipes() {
        // The actual crafting logic is handled in PrepareItemCraftEvent/CraftItemEvent.
        // Native shapeless recipes with generic Potion choices are intentionally not
        // registered because they can shadow vanilla recipes and allow invalid potion
        // combinations on some Bukkit/Paper versions.
        for (CustomRecipe custom : recipes) {
            NamespacedKey key = new NamespacedKey(plugin, "workbench_" + custom.id());
            try {
                plugin.getServer().removeRecipe(key);
            } catch (Throwable ignored) {
                // Older Bukkit builds may not expose removeRecipe(NamespacedKey).
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Match match = findMatch(inventory.getMatrix());
        if (match != null) {
            inventory.setResult(match.recipe().createResult(plugin));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CraftingInventory inventory = event.getInventory();
        Match match = findMatch(inventory.getMatrix());
        if (match == null) return;

        event.setCancelled(true);
        ItemStack result = match.recipe().createResult(plugin);
        if (!giveResult(player, result)) return;

        ItemStack[] matrix = inventory.getMatrix();
        consume(matrix, match.sourceSlot(), null, player);
        consume(matrix, match.ingredientSlot(), match.recipe().ingredient() == Material.MILK_BUCKET ? Material.BUCKET : null, player);
        inventory.setMatrix(matrix);
        Match nextMatch = findMatch(matrix);
        inventory.setResult(nextMatch == null ? null : nextMatch.recipe().createResult(plugin));
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.65f, 1.45f);
    }

    private boolean giveResult(Player player, ItemStack result) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            player.setItemOnCursor(result.clone());
            return true;
        }

        if (cursor.isSimilar(result) && cursor.getAmount() < cursor.getMaxStackSize()) {
            cursor.setAmount(cursor.getAmount() + 1);
            player.setItemOnCursor(cursor);
            return true;
        }

        return player.getInventory().addItem(result.clone()).isEmpty();
    }

    private void consume(ItemStack[] matrix, int slot, Material replacement, Player player) {
        if (slot < 0 || slot >= matrix.length) return;
        ItemStack item = matrix[slot];
        if (item == null || item.getType() == Material.AIR) return;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            matrix[slot] = item;
            if (replacement != null) player.getInventory().addItem(new ItemStack(replacement));
            return;
        }

        matrix[slot] = replacement == null ? null : new ItemStack(replacement);
    }

    private Match findMatch(ItemStack[] matrix) {
        for (CustomRecipe recipe : recipes) {
            Match match = matchRecipe(matrix, recipe);
            if (match != null) return match;
        }
        return null;
    }

    private boolean matchesIngredient(ItemStack item, Material expected) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() == expected) return true;

        // Material names changed in a few MC versions. Keep the lab recipes forgiving.
        String actual = item.getType().name();
        String wanted = expected.name();
        if (wanted.equals("REDSTONE") && actual.equals("REDSTONE_DUST")) return true;
        if (wanted.equals("FERMENTED_SPIDER_EYE") && actual.equals("FERMENTED_SPIDER_EYE")) return true;
        return false;
    }

    private Match matchRecipe(ItemStack[] matrix, CustomRecipe recipe) {
        int sourceSlot = -1;
        int ingredientSlot = -1;
        int usedSlots = 0;

        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType() == Material.AIR) continue;
            usedSlots++;

            if (sourceSlot < 0 && recipe.matchesSource(item)) {
                sourceSlot = i;
                continue;
            }
            if (ingredientSlot < 0 && matchesIngredient(item, recipe.ingredient())) {
                ingredientSlot = i;
                continue;
            }
            return null;
        }

        if (usedSlots != 2 || sourceSlot < 0 || ingredientSlot < 0 || sourceSlot == ingredientSlot) return null;
        return new Match(recipe, sourceSlot, ingredientSlot);
    }

    private record Match(CustomRecipe recipe, int sourceSlot, int ingredientSlot) { }

    private record CustomRecipe(String id, SourceKind sourceKind, SerumType sourceSerum, CompoundPotion sourceBottle,
                                Material sourceMaterial, Material ingredient,
                                SerumType resultSerum, CompoundPotion resultBottle, Material resultMaterial) {

        static CustomRecipe water(String id, Material ingredient) {
            return new CustomRecipe(id, SourceKind.WATER, null, null, Material.POTION, ingredient, null, null, Material.POTION);
        }

        static CustomRecipe awkward(String id, Material ingredient, SerumType result) {
            return new CustomRecipe(id, SourceKind.AWKWARD, null, null, Material.POTION, ingredient, result, null, Material.POTION);
        }

        static CustomRecipe serum(String id, SerumType source, Material ingredient, SerumType result) {
            return new CustomRecipe(id, SourceKind.SERUM, source, null, Material.POTION, ingredient, result, null, Material.POTION);
        }

        static CustomRecipe serum(String id, SerumType source, Material ingredient, CompoundPotion result) {
            return new CustomRecipe(id, SourceKind.SERUM, source, null, Material.POTION, ingredient, null, result, Material.POTION);
        }

        static CustomRecipe bottle(String id, CompoundPotion source, Material sourceMaterial, Material ingredient,
                                   CompoundPotion result, Material resultMaterial) {
            return new CustomRecipe(id, SourceKind.BOTTLE, null, source, sourceMaterial, ingredient, null, result, resultMaterial);
        }

        boolean matchesSource(ItemStack item) {
            if (item == null || item.getType() != sourceMaterial) return false;
            return switch (sourceKind) {
                case WATER -> ItemUtil.isWaterPotion(item);
                case AWKWARD -> ItemUtil.isAwkwardPotion(item);
                case SERUM -> Objects.equals(ItemUtil.getSerumType(item), sourceSerum);
                case BOTTLE -> Objects.equals(ItemUtil.getAnyBottleType(item), sourceBottle);
            };
        }

        ItemStack createResult(CompoundV plugin) {
            if (sourceKind == SourceKind.WATER) return ItemUtil.createAwkwardPotion();
            if (resultSerum != null) return ItemUtil.createSerum(plugin, resultSerum);
            return ItemUtil.createBottle(plugin, resultBottle, resultMaterial);
        }
    }

    private enum SourceKind {
        WATER,
        AWKWARD,
        SERUM,
        BOTTLE
    }
}
