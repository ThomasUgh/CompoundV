package de.thomasugh.compoundv.ability;

import de.thomasugh.compoundv.util.AbilityAliases;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AbilityRegistry {
    private final Map<String, Ability> map = new LinkedHashMap<>();

    public void register(Ability ability) {
        map.put(AbilityAliases.normalize(ability.getId()), ability);
    }

    public Optional<Ability> get(String id) {
        return Optional.ofNullable(map.get(AbilityAliases.normalize(id)));
    }

    public boolean contains(String id) {
        return map.containsKey(AbilityAliases.normalize(id));
    }

    public Collection<Ability> all() {
        return Collections.unmodifiableCollection(map.values());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(map.keySet());
    }
}
