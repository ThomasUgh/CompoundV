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

    @Override
    protected String actionBarColorCode() {
        return switch (stage) {
            case 2 -> "&a";
            case 3 -> "&6";
            case 4 -> "&c";
            default -> "&b";
        };
    }

    @Override
    protected String actionBarLabel() {
        return "Heatvision";
    }

    @Override
    protected String coloredActionBarLabel() {
        String suffix = switch (stage) {
            case 2 -> " &7II";
            case 3 -> " &7III";
            case 4 -> " &7IV";
            default -> " &7I";
        };
        return actionBarColorCode() + actionBarLabel() + "&r" + suffix;
    }

    @Override public String getDescriptionKey() { return "ability.heat_vision.description"; }

    @Override
    protected double range() {
        return plugin.getConfig().getDouble(stagePath("range"), switch (stage) {
            case 2 -> 35.0;
            case 3 -> 37.0;
            case 4 -> 40.0;
            default -> 30.0;
        });
    }

    @Override
    protected double damageAmount() {
        double hearts = plugin.getConfig().getDouble(stagePath("damage_hearts"), switch (stage) {
            case 2 -> 2.0;
            case 3 -> 2.25;
            case 4 -> 2.7;
            default -> 1.35;
        });
        return Math.max(0.0, hearts) * 2.0;
    }

    @Override
    protected Color coreColor() {
        return switch (stage) {
            case 2 -> Color.fromRGB(45, 255, 105);
            case 3 -> Color.fromRGB(255, 135, 25);
            case 4 -> Color.fromRGB(255, 35, 18);
            default -> Color.fromRGB(45, 210, 255);
        };
    }

    @Override
    protected Color glowColor() {
        return switch (stage) {
            case 2 -> Color.fromRGB(155, 255, 175);
            case 3 -> Color.fromRGB(255, 190, 80);
            case 4 -> Color.fromRGB(255, 100, 65);
            default -> Color.fromRGB(150, 235, 255);
        };
    }

    @Override protected boolean cooksMeatDrops() { return true; }

    @Override protected int coreParticles() {
        return Math.max(0, plugin.getConfig().getInt(stagePath("core_particles"), stage >= 3 ? 2 : super.coreParticles()));
    }

    @Override protected int glowParticles() {
        return Math.max(0, plugin.getConfig().getInt(stagePath("glow_particles"), stage == 4 ? 1 : super.glowParticles()));
    }

    @Override protected int impactParticles() {
        return Math.max(0, plugin.getConfig().getInt(stagePath("impact_particles"), stage == 4 ? 16 : stage == 3 ? 14 : super.impactParticles()));
    }

    @Override protected float size() {
        return (float) Math.max(0.01, particleDouble("core_size", super.size()));
    }

    @Override protected float glowSize() {
        return (float) Math.max(0.01, particleDouble("glow_size", super.glowSize()));
    }

    @Override protected double step() {
        return Math.max(0.08, particleDouble("step", super.step()));
    }

    private double particleDouble(String key, double fallback) {
        String globalPath = "heat_vision.particles." + key;
        double global = plugin.getConfig().getDouble(globalPath, fallback);

        // Global particle tuning is the default for every Heatvision tier.
        // Stage-specific tuning only wins when explicitly enabled so changing
        // heat_vision.particles.step/core_size/glow_size always affects I-IV.
        if (plugin.getConfig().getBoolean(stagePath("custom_particles"), false)) {
            return plugin.getConfig().getDouble(stagePath(key), global);
        }
        return global;
    }

    @Override protected String activeSoundConfigPath() {
        return stagePath("active_sound");
    }

    @Override protected String fallbackActiveSoundConfigPath() {
        return "heat_vision.active_sound";
    }

    private String stagePath(String key) {
        return "heat_vision.stages.stage_" + stage + "." + key;
    }
}
