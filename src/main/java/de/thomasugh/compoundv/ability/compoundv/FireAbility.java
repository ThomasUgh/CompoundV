package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FireAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Integer> ticker = new HashMap<>();
    public FireAbility(CompoundV p) { plugin = p; }

    @Override public String    getId()          { return "fire"; }
    @Override public String    getDisplayName() { return "Fire Control"; }
    @Override public int getColor()       { return 0xFF5500; }
    @Override public boolean   needsTick()      { return true; }

    @Override
    public void apply(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
    }

    @Override
    public void remove(Player p) {
        ticker.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
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
