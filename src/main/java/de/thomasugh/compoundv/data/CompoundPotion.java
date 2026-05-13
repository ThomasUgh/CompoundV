package de.thomasugh.compoundv.data;

import org.bukkit.Color;

public enum CompoundPotion {

    COMPOUND_V("Compound V", Color.fromRGB(100, 200, 255), false, false),
    TEMP_V    ("Temp V",     Color.fromRGB(40,  200, 60),  true,  false),
    V_ONE     ("V One",      Color.fromRGB(20,  40,  220), false, false),
    ANTI_V    ("Anti V",     Color.fromRGB(220, 20,  20),  false, true);

    private final String  name;
    private final Color   color;
    private final boolean temp;
    private final boolean removerPotion;

    CompoundPotion(String n, Color c, boolean t, boolean remover) {
        this.name = n; this.color = c; this.temp = t; this.removerPotion = remover;
    }

    public String  getDisplayName() { return name; }
    public Color   getPotionColor() { return color; }
    public boolean isTemporary()    { return temp; }
    public boolean isRemover()      { return removerPotion; }
    public String  getConfigKey()   { return name().toLowerCase(); }
}
