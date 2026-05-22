package de.thomasugh.compoundv.util;

import java.util.Locale;
import java.util.Map;

public final class AbilityAliases {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("homelander", "the_patriot"),
            Map.entry("home", "the_patriot"),
            Map.entry("patriot", "the_patriot"),
            Map.entry("thepatriot", "the_patriot"),
            Map.entry("the-patriot", "the_patriot"),
            Map.entry("the_patriot", "the_patriot"),

            Map.entry("vone_homelander", "the_patriot_v_one"),
            Map.entry("v_one_homelander", "the_patriot_v_one"),
            Map.entry("patriot_v1", "the_patriot_v_one"),
            Map.entry("patriotv1", "the_patriot_v_one"),
            Map.entry("patriot_v_one", "the_patriot_v_one"),
            Map.entry("the_patriot_v1", "the_patriot_v_one"),
            Map.entry("the_patriot_v_one", "the_patriot_v_one"),

            Map.entry("soldier_boy", "the_veteran"),
            Map.entry("soldierboy", "the_veteran"),
            Map.entry("veteran", "the_veteran"),
            Map.entry("theveteran", "the_veteran"),
            Map.entry("the-veteran", "the_veteran"),
            Map.entry("the_veteran", "the_veteran"),

            Map.entry("diver", "the_diver"),
            Map.entry("thediver", "the_diver"),
            Map.entry("the-diver", "the_diver"),
            Map.entry("the_deep", "the_diver"),
            Map.entry("deep", "the_diver"),
            Map.entry("sonar", "the_diver"),

            Map.entry("runner", "the_runner"),
            Map.entry("therunner", "the_runner"),
            Map.entry("the-runner", "the_runner"),
            Map.entry("the_runner", "the_runner"),
            Map.entry("water_runner", "the_runner"),
            Map.entry("waterwalker", "the_runner"),

            Map.entry("tp", "teleporter"),
            Map.entry("teleport", "teleporter"),
            Map.entry("blink", "teleporter"),

            Map.entry("xray", "vision"),
            Map.entry("x-ray", "vision"),
            Map.entry("x_ray", "vision"),
            Map.entry("visions", "vision"),

            Map.entry("heatvision", "heat_vision"),
            Map.entry("heat_vision_1", "heat_vision"),
            Map.entry("heatvision1", "heat_vision"),
            Map.entry("heat_vision_i", "heat_vision"),
            Map.entry("heatvisioni", "heat_vision"),
            Map.entry("heatvision2", "heat_vision_2"),
            Map.entry("heat_vision_ii", "heat_vision_2"),
            Map.entry("heatvisionii", "heat_vision_2"),
            Map.entry("heatvision3", "heat_vision_3"),
            Map.entry("heat_vision_iii", "heat_vision_3"),
            Map.entry("heatvisioniii", "heat_vision_3"),

            Map.entry("flight", "fly"),
            Map.entry("the_ghost", "invisibility"),
            Map.entry("ghost", "invisibility"),
            Map.entry("strength_men", "strength"),
            Map.entry("strengthmen", "strength"),
            Map.entry("fire_control", "fire"),
            Map.entry("firecontrol", "fire")
    );

    private AbilityAliases() {}

    public static String normalize(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
