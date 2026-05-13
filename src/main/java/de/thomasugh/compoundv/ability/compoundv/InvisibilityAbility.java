package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InvisibilityAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Set<UUID> invisible = new HashSet<>();

    public InvisibilityAbility(CompoundVPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String    getId()          { return "invisibility"; }
    @Override public String    getDisplayName() { return "The Ghost"; }
    @Override public TextColor getColor()       { return TextColor.color(0xAAAAAA); }
    @Override public boolean   hasToggle()      { return true; }

    @Override
    public void apply(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 1, false, false, true));
    }

    @Override
    public void remove(Player p) {
        invisible.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
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
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 1, false, false, false));
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.035);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.7f);
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.ghost_on"));
        } else {
            invisible.remove(uuid);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.03);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
            p.sendActionBar(plugin.getLocaleManager().msg("toggle.ghost_off"));
        }
    }
}
