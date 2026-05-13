package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FireAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Map<UUID, Integer> ticker = new HashMap<>();
    public FireAbility(CompoundVPlugin p) { plugin = p; }

    @Override public String    getId()          { return "fire"; }
    @Override public String    getDisplayName() { return "Fire Control"; }
    @Override public TextColor getColor()       { return TextColor.color(0xFF5500); }
    @Override public boolean   needsTick()      { return true; }

    @Override
    public void apply(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
    }

    @Override
    public void remove(Player p) {
        ticker.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    @Override
    public void onTick(Player p) {
        if (ticker.merge(p.getUniqueId(), 1, Integer::sum) % 10 != 0) return;
        double r = plugin.getConfig().getDouble("abilities.fire.radius", 3.5);
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof LivingEntity le && e != p) le.setFireTicks(60);
        }
    }
}
