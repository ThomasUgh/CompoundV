package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedsterAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Map<UUID, Boolean> speedOn = new HashMap<>();

    public SpeedsterAbility(CompoundVPlugin p) { plugin = p; }

    @Override public String    getId()          { return "speedster"; }
    @Override public String    getDisplayName() { return "Speedster"; }
    @Override public TextColor getColor()       { return TextColor.color(0xFFDD00); }
    @Override public boolean   hasToggle()      { return true; }

    @Override
    public void apply(Player p) {
        int res = plugin.getConfig().getInt("abilities.speedster.resistance_level", 1);
        if (res > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Integer.MAX_VALUE, res - 1, false, false, true));
        }
    }

    @Override
    public void remove(Player p) {
        speedOn.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    @Override
    public void onToggle(Player p) {
        boolean next = !speedOn.getOrDefault(p.getUniqueId(), false);
        speedOn.put(p.getUniqueId(), next);
        if (next) {
            int lvl = plugin.getConfig().getInt("abilities.speedster.speed_level", 4);

            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, Math.max(0, lvl - 1), false, false, true));
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.speed_on"));
        } else {
            p.removePotionEffect(PotionEffectType.SPEED);
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.speed_off"));
        }
    }
}
