package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TheRunnerAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey healthModKey;
    private final NamespacedKey attackSpeedKey;
    private final Map<UUID, Integer> speedLevels = new HashMap<>();
    private final Set<String> temporaryIce = new HashSet<>();

    public TheRunnerAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthModKey = new NamespacedKey(plugin, "runner_hearts");
        this.attackSpeedKey = new NamespacedKey(plugin, "runner_attack_speed");
    }

    @Override public String getId() { return "the_runner"; }
    @Override public String getDisplayName() { return "The Runner"; }
    @Override public int getColor() { return 0xFFE066; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int resistance = plugin.getConfig().getInt("abilities.the_runner.resistance_level", 1);
        int strength = plugin.getConfig().getInt("abilities.the_runner.strength_level", 2);
        int startSpeed = Math.max(minSpeedLevel(), Math.min(maxSpeedLevel(),
                plugin.getConfig().getInt("abilities.the_runner.default_speed_level", minSpeedLevel())));

        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, resistance - 1, false, false, true));
        }
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, strength - 1, false, false, true));
        }

        AttributeUtil.setMaxHealthBonus(player, healthModKey,
                plugin.getConfig().getDouble("abilities.the_runner.extra_hearts", 5.0) * 2.0);
        AttributeUtil.setAttackSpeedBonus(player, attackSpeedKey,
                plugin.getConfig().getDouble("abilities.the_runner.attack_speed_bonus", 1024.0));

        speedLevels.put(player.getUniqueId(), startSpeed);
        applySpeed(player, startSpeed);
    }

    @Override
    public void remove(Player player) {
        speedLevels.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.SPEED);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        AttributeUtil.setMaxHealthBonus(player, healthModKey, 0);
        AttributeUtil.setAttackSpeedBonus(player, attackSpeedKey, 0);
    }

    @Override
    public void onToggle(Player player) {
        int min = minSpeedLevel();
        int max = maxSpeedLevel();
        UUID uuid = player.getUniqueId();
        Integer current = speedLevels.get(uuid);

        if (current == null) {
            speedLevels.put(uuid, min);
            applySpeed(player, min);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_level",
                    "level", Integer.toString(min)));
            return;
        }

        if (current >= max) {
            speedLevels.remove(uuid);
            player.removePotionEffect(PotionEffects.SPEED);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_off"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 1.6f);
            return;
        }

        int next = current + 1;
        speedLevels.put(uuid, next);
        applySpeed(player, next);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_level",
                "level", Integer.toString(next)));
    }

    @Override
    public void onTick(Player player) {
        if (plugin.getConfig().getBoolean("abilities.the_runner.water_walk", true)) {
            freezeWaterAround(player);
        }
    }

    private void applySpeed(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffects.SPEED,
                Integer.MAX_VALUE, Math.max(0, level - 1), false, false, true));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.9f);
    }

    private int minSpeedLevel() {
        return Math.max(1, plugin.getConfig().getInt("abilities.the_runner.speed_min_level", 9));
    }

    private int maxSpeedLevel() {
        return Math.max(minSpeedLevel(), plugin.getConfig().getInt("abilities.the_runner.speed_max_level", 12));
    }

    private void freezeWaterAround(Player player) {
        Location base = player.getLocation().clone().subtract(0, 1, 0);
        World world = base.getWorld();
        if (world == null) return;

        int radius = Math.max(1, plugin.getConfig().getInt("abilities.the_runner.water_walk_radius", 2));
        int y = base.getBlockY();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                Block block = world.getBlockAt(base.getBlockX() + x, y, base.getBlockZ() + z);
                if (block.getType() != Material.WATER) continue;
                freezeBlock(block);
            }
        }
    }

    private void freezeBlock(Block block) {
        String key = blockKey(block);
        if (!temporaryIce.add(key)) return;

        block.setType(Material.FROSTED_ICE, false);
        int thawTicks = Math.max(20, plugin.getConfig().getInt("abilities.the_runner.water_walk_ice_ticks", 100));
        SchedulerAdapter.runLater(plugin, () -> {
            temporaryIce.remove(key);
            if (block.getType() == Material.FROSTED_ICE) {
                block.setType(Material.WATER, false);
            }
        }, thawTicks);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
