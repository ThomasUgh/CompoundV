package de.thomasugh.compoundv.data;

import org.bukkit.Color;

public enum SerumType {

    CRYSTALLINE_SERUM("Crystalline Serum", Color.fromRGB(120, 235, 255)),
    RESONANT_V_SERUM("Resonant V Serum", Color.fromRGB(90, 160, 255)),
    ACTIVATED_V_SERUM("Activated V Serum", Color.fromRGB(70, 230, 255)),

    UNSTABLE_TEMP_V("Unstable Temp V", Color.fromRGB(80, 240, 90)),
    UNSTABLE_V_SERUM("Unstable V Serum", Color.fromRGB(80, 190, 255)),

    DRACONIC_SERUM("Draconic Serum", Color.fromRGB(110, 65, 255)),
    ECHO_CHARGED_SERUM("Echo-Charged Serum", Color.fromRGB(60, 90, 230)),
    REINFORCED_SERUM("Reinforced Serum", Color.fromRGB(35, 50, 160)),
    UNSTABLE_V_ONE_SERUM("Unstable V-One Serum", Color.fromRGB(25, 35, 120)),

    CLEANSING_SERUM("Cleansing Serum", Color.fromRGB(220, 255, 245)),
    RESTORATIVE_SERUM("Restorative Serum", Color.fromRGB(255, 120, 120)),

    DECAY_SERUM("Decay Serum", Color.fromRGB(60, 95, 50)),
    CORRUPTED_SERUM("Corrupted Serum", Color.fromRGB(45, 20, 55)),
    RESONANT_PATHOGEN("Resonant Pathogen", Color.fromRGB(45, 120, 80)),
    AIRBORNE_V_PATHOGEN("Airborne V-Pathogen", Color.fromRGB(70, 145, 105)),
    STABILIZED_V_NULL_PATHOGEN("Stabilized V-Null Pathogen", Color.fromRGB(20, 125, 60));

    private final String displayName;
    private final Color color;

    SerumType(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }

    public String getConfigKey() {
        return name().toLowerCase();
    }
}
