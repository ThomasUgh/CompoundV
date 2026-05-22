package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.shared.BaseHeatVisionAbility;
import org.bukkit.Color;

public class HeatVisionAbility extends BaseHeatVisionAbility {

    private final String id;
    private final String displayName;
    private final int stage;
    private final int color;

    public HeatVisionAbility(CompoundV plugin, String id, String displayName, int stage, int color) {
        super(plugin);
        this.id = id;
        this.displayName = displayName;
        this.stage = stage;
        this.color = color;
    }

    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return displayName; }
    @Override public int getColor() { return color; }
    @Override public String getDescriptionKey() { return "ability.heat_vision.description"; }

    @Override
    protected double range() {
        return plugin.getConfig().getDouble(stagePath("range"), switch (stage) {
            case 2 -> 35.0;
            case 3 -> 40.0;
            default -> 30.0;
        });
    }

    @Override
    protected double damageAmount() {
        double hearts = plugin.getConfig().getDouble(stagePath("damage_hearts"), switch (stage) {
            case 2 -> 2.5;
            case 3 -> 3.0;
            default -> 1.5;
        });
        return Math.max(0.0, hearts) * 2.0;
    }

    @Override
    protected Color coreColor() {
        return switch (stage) {
            case 2 -> Color.fromRGB(45, 255, 105);
            case 3 -> Color.fromRGB(255, 35, 18);
            default -> Color.fromRGB(45, 210, 255);
        };
    }

    @Override
    protected Color glowColor() {
        return switch (stage) {
            case 2 -> Color.fromRGB(155, 255, 175);
            case 3 -> Color.fromRGB(255, 100, 65);
            default -> Color.fromRGB(150, 235, 255);
        };
    }

    @Override protected int coreParticles() { return stage == 3 ? 2 : super.coreParticles(); }
    @Override protected int glowParticles() { return stage == 3 ? 1 : super.glowParticles(); }
    @Override protected int impactParticles() { return stage == 3 ? 16 : super.impactParticles(); }
    @Override protected float size() { return stage == 3 ? 0.64f : super.size(); }
    @Override protected float glowSize() { return stage == 3 ? 0.42f : super.glowSize(); }
    @Override protected double step() { return stage == 3 ? 0.32 : super.step(); }

    private String stagePath(String key) {
        return "heat_vision.stages.stage_" + stage + "." + key;
    }
}
