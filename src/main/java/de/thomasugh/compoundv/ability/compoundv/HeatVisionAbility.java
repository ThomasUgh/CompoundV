package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.shared.BaseHeatVisionAbility;
import net.kyori.adventure.text.format.TextColor;

public class HeatVisionAbility extends BaseHeatVisionAbility {

    public HeatVisionAbility(CompoundVPlugin plugin) { super(plugin); }

    @Override public String    getId()          { return "heat_vision"; }
    @Override public String    getDisplayName() { return "Heat Vision"; }
    @Override public TextColor getColor()       { return TextColor.color(0xFF4400); }

}
