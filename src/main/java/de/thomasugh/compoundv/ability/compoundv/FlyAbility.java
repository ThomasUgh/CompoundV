package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.ability.Ability;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class FlyAbility implements Ability {
    @Override public String getId() { return "fly"; }
    @Override public String getDisplayName() { return "Flight"; }
    @Override public int getColor() { return 0x44DDFF; }

    @Override
    public void apply(Player player) {
        player.setAllowFlight(true);
    }

    @Override
    public void remove(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }
}
