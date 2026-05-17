package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;

public class StrengthAbility implements Ability {

    private final CompoundV plugin;
    public StrengthAbility(CompoundV p) { plugin = p; }

    @Override public String    getId()          { return "strength"; }
    @Override public String    getDisplayName() { return "Strength-Men"; }
    @Override public int getColor()       { return 0xFF6600; }

    @Override
    public void apply(Player p) {
        int str = plugin.getConfig().getInt("abilities.strength.level", 2);
        int res = plugin.getConfig().getInt("abilities.strength.resistance_level", 0);
        p.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, str - 1), false, false, true));
        if (res > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, res - 1, false, false, true));
        }
    }

    @Override
    public void remove(Player p) {
        p.removePotionEffect(PotionEffects.STRENGTH);
        p.removePotionEffect(PotionEffects.RESISTANCE);
    }
}
