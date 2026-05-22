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

    @Override public String getId() { return "invisibility"; }
    @Override public String getDisplayName() { return "The Ghost"; }
    @Override public int getColor() { return 0xAAAAAA; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int resistance = plugin.getConfig().getInt("abilities.invisibility.resistance_level", 2);
        int strength = plugin.getConfig().getInt("abilities.invisibility.strength_level", 1);

        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, resistance - 1, false, false, true));
        }
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, strength - 1, false, false, true));
        }
    }

    @Override
    public void remove(Player player) {
        invisible.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.INVISIBILITY);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
    }

    @Override
    public void onToggle(Player player) {
        toggleGhostMode(player);
    }

    public boolean isInvisible(Player player) {
        return invisible.contains(player.getUniqueId()) && player.hasPotionEffect(PotionEffects.INVISIBILITY);
    }

    public boolean hidesFromMobs() {
        return plugin.getConfig().getBoolean("abilities.invisibility.hide_from_mobs", true);
    }

    public void toggleGhostMode(Player player) {
        UUID uuid = player.getUniqueId();
        boolean next = !invisible.contains(uuid);
        if (next) {
            invisible.add(uuid);
            player.addPotionEffect(new PotionEffect(PotionEffects.INVISIBILITY,
                    Integer.MAX_VALUE, 1, false, false, false));
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.035);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.7f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.ghost_on"));
        } else {
            invisible.remove(uuid);
            player.removePotionEffect(PotionEffects.INVISIBILITY);
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.ghost_off"));
        }
    }
}
