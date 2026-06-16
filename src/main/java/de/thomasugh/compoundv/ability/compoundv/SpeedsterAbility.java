package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedsterAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey stepKey;
    private final Map<UUID, Boolean> speedOn = new HashMap<>();

    public SpeedsterAbility(CompoundV p) {
        plugin = p;
        stepKey = new NamespacedKey(p, "speedster_step");
    }

    @Override public String    getId()          { return "speedster"; }
    @Override public String    getDisplayName() { return "Speedster"; }
    @Override public int getColor()       { return 0xFFDD00; }
    @Override public boolean   hasToggle()      { return true; }

    @Override
    public void apply(Player p) {
        int str = plugin.getConfig().getInt("abilities.speedster.strength_level", 1);
        int res = plugin.getConfig().getInt("abilities.speedster.resistance_level", 1);
        p.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, str - 1), false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, res - 1), false, false, true));

        double stepBonus = plugin.getConfig().getDouble("abilities.speedster.step_height_bonus", 0.9);
        AttributeUtil.setStepHeightBonus(p, stepKey, stepBonus);
    }

    @Override
    public void remove(Player p) {
        speedOn.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffects.STRENGTH);
        p.removePotionEffect(PotionEffects.RESISTANCE);
        p.removePotionEffect(PotionEffects.SPEED);
        AttributeUtil.setStepHeightBonus(p, stepKey, 0);
    }

    @Override
    public void onToggle(Player p) {
        boolean next = !speedOn.getOrDefault(p.getUniqueId(), false);
        speedOn.put(p.getUniqueId(), next);
        if (next) {
            int lvl = plugin.getConfig().getInt("abilities.speedster.speed_level", 4);

            p.addPotionEffect(new PotionEffect(PotionEffects.SPEED,
                    Integer.MAX_VALUE, Math.max(0, lvl - 1), false, false, true));
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.speed_on"));
        } else {
            p.removePotionEffect(PotionEffects.SPEED);
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.speed_off"));
        }
    }
}
