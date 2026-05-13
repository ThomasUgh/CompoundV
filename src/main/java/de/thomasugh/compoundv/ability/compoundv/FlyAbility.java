package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public class FlyAbility implements Ability {
    @Override public String    getId()          { return "fly"; }
    @Override public String    getDisplayName() { return "Flight"; }
    @Override public TextColor getColor()       { return TextColor.color(0x44DDFF); }

    @Override public void apply(Player p)  { p.setAllowFlight(true); }
    @Override public void remove(Player p) { p.setAllowFlight(false); p.setFlying(false); }
}
