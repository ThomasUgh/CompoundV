package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class StrengthAbility implements Ability {

    private final CompoundVPlugin plugin;
    public StrengthAbility(CompoundVPlugin p) { plugin = p; }

    @Override public String    getId()          { return "strength"; }
    @Override public String    getDisplayName() { return "Strength-Men"; }
    @Override public TextColor getColor()       { return TextColor.color(0xFF6600); }

    @Override
    public void apply(Player p) {
        int str = plugin.getConfig().getInt("abilities.strength.level", 2);
        int res = plugin.getConfig().getInt("abilities.strength.resistance_level", 0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, str - 1), false, false, true));
        if (res > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Integer.MAX_VALUE, res - 1, false, false, true));
        }
    }

    @Override
    public void remove(Player p) {
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
    }
}
