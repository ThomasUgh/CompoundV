package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public class SizeChangerAbility implements Ability {

    private final CompoundV plugin;

    public SizeChangerAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "size_changer"; }
    @Override public String getDisplayName() { return "SizeChanger"; }
    @Override public int getColor() { return 0x9C64FF; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.size_changer.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.size_changer.resistance_level", 2);

        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }
}
