package de.thomasugh.compoundv.ability;

import org.bukkit.entity.Player;

public interface Ability {

    String    getId();
    String    getDisplayName();
    int getColor();

    void apply(Player player);
    void remove(Player player);

    default boolean needsTick()             { return false; }
    default void    onTick(Player player)   {}

    default boolean hasToggle()             { return false; }
    default void    onToggle(Player player) {}

    default String getDescriptionKey() { return "ability." + getId() + ".description"; }
}
