package de.thomasugh.compoundv.util;

import java.util.Set;

public final class VOneAbilities {

    private static final Set<String> PURE_V_ONE = Set.of(
            "the_veteran",
            "sonic_boom",
            "stormstrike",
            "heal_angel",
            "submarine"
    );

    private VOneAbilities() {
    }

    public static boolean isUpgrade(String abilityId) {
        if (abilityId == null) return false;
        return AbilityAliases.normalize(abilityId).endsWith("_v_one");
    }

    public static boolean isPure(String abilityId) {
        if (abilityId == null) return false;
        return PURE_V_ONE.contains(AbilityAliases.normalize(abilityId));
    }

    public static boolean isVOne(String abilityId) {
        return isUpgrade(abilityId) || isPure(abilityId);
    }
}
