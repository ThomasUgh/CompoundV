package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.shared.BaseHeatVisionAbility;

public class HeatVisionAbility extends BaseHeatVisionAbility {

    public HeatVisionAbility(CompoundV plugin) { super(plugin); }

    @Override public String    getId()          { return "heat_vision"; }
    @Override public String    getDisplayName() { return "Heat Vision"; }
    @Override public int getColor()       { return 0xFF4400; }

}
