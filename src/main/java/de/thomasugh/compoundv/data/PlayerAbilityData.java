package de.thomasugh.compoundv.data;
public record PlayerAbilityData(String abilityId, CompoundPotion potionType, long expiresAt) {
    public boolean isTemporary() { return expiresAt > 0; }
    public boolean isExpired()   { return isTemporary() && System.currentTimeMillis() > expiresAt; }
    public long    remainingSec(){ return isTemporary() ? Math.max(0, expiresAt - System.currentTimeMillis()) / 1000 : Long.MAX_VALUE; }
}