package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InvisibilityAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> invisible = new HashSet<>();

    public InvisibilityAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String    getId()          { return "invisibility"; }
    @Override public String    getDisplayName() { return "The Ghost"; }
    @Override public int getColor()       { return 0xAAAAAA; }
    @Override public boolean   hasToggle()      { return true; }

    @Override
    public void apply(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, 1, false, false, true));
    }

    @Override
    public void remove(Player p) {
        invisible.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffects.INVISIBILITY);
        p.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player p) {
        toggleGhostMode(p);
    }

    public void toggleGhostMode(Player p) {
        UUID uuid = p.getUniqueId();
        boolean next = !invisible.contains(uuid);
        if (next) {
            invisible.add(uuid);
            p.addPotionEffect(new PotionEffect(PotionEffects.INVISIBILITY,
                    Integer.MAX_VALUE, 1, false, false, false));
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.035);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.7f);
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.ghost_on"));
        } else {
            invisible.remove(uuid);
            p.removePotionEffect(PotionEffects.INVISIBILITY);
            p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.03);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
            MessageUtil.sendActionBar(p, plugin.getLocaleManager().msg("toggle.ghost_off"));
        }
    }
}
