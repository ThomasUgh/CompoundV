package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheWormAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Boolean> nightVision = new HashMap<>();

    public TheWormAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_worm"; }
    @Override public String getDisplayName() { return "The Worm"; }
    @Override public int getColor() { return 0x8D6E63; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int haste = plugin.getConfig().getInt("abilities.the_worm.haste_level", 4);
        int strength = plugin.getConfig().getInt("abilities.the_worm.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.the_worm.resistance_level", 1);

        player.addPotionEffect(new PotionEffect(PotionEffects.HASTE,
                Integer.MAX_VALUE, Math.max(0, haste - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        nightVision.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.HASTE);
        player.removePotionEffect(PotionEffects.NIGHT_VISION);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player player) {
        boolean next = !nightVision.getOrDefault(player.getUniqueId(), false);
        nightVision.put(player.getUniqueId(), next);
        if (next) {
            player.addPotionEffect(new PotionEffect(PotionEffects.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.worm_night_vision_on"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 0.75f);
        } else {
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.worm_night_vision_off"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.35f, 0.9f);
        }
    }
}
