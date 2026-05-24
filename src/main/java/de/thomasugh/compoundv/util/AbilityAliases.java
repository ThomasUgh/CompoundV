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

            Map.entry("sonic", "sonic_boom"),
            Map.entry("sonicboom", "sonic_boom"),
            Map.entry("sonic_boom", "sonic_boom"),
            Map.entry("sonic-boom", "sonic_boom"),
            Map.entry("bombsight", "sonic_boom"),

            Map.entry("sizechanger", "size_changer"),
            Map.entry("size_changer", "size_changer"),
            Map.entry("size-changer", "size_changer"),
            Map.entry("size", "size_changer"),
            Map.entry("sizechanger_v_one", "size_changer_v_one"),
            Map.entry("size_changer_v_one", "size_changer_v_one"),
            Map.entry("size-changer-v-one", "size_changer_v_one"),
            Map.entry("size_v_one", "size_changer_v_one"),

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
            Map.entry("teleporter_v_one", "teleporter_v_one"),
            Map.entry("teleporter-v-one", "teleporter_v_one"),
            Map.entry("tp_v_one", "teleporter_v_one"),
            Map.entry("blink_v_one", "teleporter_v_one"),

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
            Map.entry("heatvision4", "heat_vision_4"),
            Map.entry("heat_vision_4", "heat_vision_4"),
            Map.entry("heat_vision_iv", "heat_vision_4"),
            Map.entry("heatvisioniv", "heat_vision_4"),

            Map.entry("flight", "fly"),
            Map.entry("invisibility", "the_ghost"),
            Map.entry("invisible", "the_ghost"),
            Map.entry("translucent", "the_ghost"),
            Map.entry("the_ghost", "the_ghost"),
            Map.entry("the-ghost", "the_ghost"),
            Map.entry("ghost", "the_ghost"),
            Map.entry("strength_men", "strength"),
            Map.entry("strengthmen", "strength"),
            Map.entry("fire_control", "fire"),
            Map.entry("firecontrol", "fire"),

            Map.entry("jump", "jumper"),
            Map.entry("jumper", "jumper"),

            Map.entry("shock", "shockwave"),
            Map.entry("shock_wave", "shockwave"),
            Map.entry("shockwave", "shockwave"),
            Map.entry("shock-wave", "shockwave"),

            Map.entry("storm", "stormstrike"),
            Map.entry("stormstrike", "stormstrike"),
            Map.entry("storm_strike", "stormstrike"),
            Map.entry("storm-strike", "stormstrike"),
            Map.entry("thunderstrike", "stormstrike"),
            Map.entry("thunder_strike", "stormstrike"),

            Map.entry("worm", "the_worm"),
            Map.entry("theworm", "the_worm"),
            Map.entry("the_worm", "the_worm"),
            Map.entry("the-worm", "the_worm"),

            Map.entry("flashlight", "flash_light"),
            Map.entry("flash_light", "flash_light"),
            Map.entry("flash-light", "flash_light"),
            Map.entry("starlight", "flash_light"),

            Map.entry("fire_sonic", "fire_sonic"),
            Map.entry("firesonic", "fire_sonic"),
            Map.entry("fire-sonic", "fire_sonic"),

            Map.entry("toxic_cloud", "toxic_cloud"),
            Map.entry("toxiccloud", "toxic_cloud"),
            Map.entry("toxic-cloud", "toxic_cloud"),

            Map.entry("the_countess", "the_countess"),
            Map.entry("countess", "the_countess"),
            Map.entry("crimson_countess", "the_countess"),

            Map.entry("the_warrior", "the_warrior"),
            Map.entry("warrior", "the_warrior"),
            Map.entry("queen_maeve", "the_warrior"),
            Map.entry("maeve", "the_warrior"),

            Map.entry("the_headpopper", "the_headpopper"),
            Map.entry("headpopper", "the_headpopper"),
            Map.entry("victoria_neuman", "the_headpopper"),
            Map.entry("neuman", "the_headpopper"),

            Map.entry("spider_weaver", "spider_weaver"),
            Map.entry("spiderweaver", "spider_weaver"),
            Map.entry("webweaver", "spider_weaver"),
            Map.entry("web_weaver", "spider_weaver")
    );

    private AbilityAliases() {}

    public static String normalize(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
