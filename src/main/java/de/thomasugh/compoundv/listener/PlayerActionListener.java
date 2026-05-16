package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.ability.compoundv.FireAbility;
import de.thomasugh.compoundv.ability.compoundv.TheDiverAbility;
import de.thomasugh.compoundv.ability.compoundv.VisionAbility;
import de.thomasugh.compoundv.ability.shared.ThePatriotAbility;
import de.thomasugh.compoundv.ability.vone.TheVeteranAbility;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.manager.PotionRollManager;
import de.thomasugh.compoundv.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerActionListener implements Listener {

    private final CompoundVPlugin   plugin;
    private final AbilityManager    manager;
    private final PotionRollManager roller;

    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final Map<UUID, Double>  fallPeakY   = new HashMap<>();

    public PlayerActionListener(CompoundVPlugin plugin, AbilityManager manager, PotionRollManager roller) {
        this.plugin = plugin; this.manager = manager; this.roller = roller;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (e.isCancelled()) return;
        CompoundPotion type = ItemUtil.getBottleType(e.getItem());
        if (type == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        consumeBottle(p, e.getItem());

        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);

        if (type == CompoundPotion.ANTI_V) {
            if (manager.hasAbility(p)) {
                manager.removeAndForget(p);
                p.sendMessage(plugin.getLocaleManager().msg("ability.removed_anti_v"));
            } else {
                p.sendMessage(plugin.getLocaleManager().msg("potion.no_ability"));
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, true));
            return;
        }

        if (type == CompoundPotion.V_ONE) {
            int ticks = plugin.getConfig().getInt("v_one.drink_effect_seconds", 5) * 20;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  ticks, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  ticks, 2, false, true));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.8f, 0.6f);
            p.sendMessage(plugin.getLocaleManager().msg("potion.drink_v_one"));
        } else if (type == CompoundPotion.TEMP_V) {
            p.sendMessage(plugin.getLocaleManager().msg("potion.drink_temp_v"));
        } else {
            p.sendMessage(plugin.getLocaleManager().msg("potion.drinking"));
        }

        roller.roll(p, type);
    }

    private void consumeBottle(Player p, ItemStack consumed) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off  = p.getInventory().getItemInOffHand();
        if (main != null && main.isSimilar(consumed)) { decrementOrReplace(p, true,  main); return; }
        if (off  != null && off.isSimilar(consumed))  { decrementOrReplace(p, false, off);  return; }

        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && it.isSimilar(consumed)) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                    p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
                } else {
                    p.getInventory().setItem(i, new ItemStack(Material.GLASS_BOTTLE));
                }
                return;
            }
        }
    }

    private void decrementOrReplace(Player p, boolean mainHand, ItemStack stack) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        } else if (mainHand) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        } else {
            p.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Ability ab = manager.getAbility(e.getPlayer());
        if (ab == null || !ab.hasToggle()) return;
        e.setCancelled(true);
        ab.onToggle(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) return;
        Player p = e.getPlayer();

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK
                && a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        if (ItemUtil.isCompoundVBottle(p.getInventory().getItemInMainHand())) return;
        if (ItemUtil.isCompoundVBottle(p.getInventory().getItemInOffHand()))  return;

        Ability ab = manager.getAbility(p);
        if (ab == null) return;

        if (ab instanceof FireAbility && isFireStarter(e.getItem())) {
            cancelAbilityInteraction(e);
            return;
        }

        if (e.getHand() != EquipmentSlot.HAND) return;

        if (ab instanceof ThePatriotAbility ha) {
            if (canUseSneakLeftClick(p, a)) {
                cancelAbilityInteraction(e);
                ha.toggleGlowRadar(p);
            }
            return;
        }

        if (ab instanceof TheDiverAbility diver) {
            if (canUseSneakLeftClick(p, a)) {
                cancelAbilityInteraction(e);
                diver.toggleSonar(p);
            }
            return;
        }

        if (ab instanceof VisionAbility vision) {
            if (canUseSneakLeftClick(p, a)) {
                cancelAbilityInteraction(e);
                vision.toggleXray(p);
            }
            return;
        }

        if (ab instanceof TheVeteranAbility veteran) {
            if (canUseSneakLeftClick(p, a)) {
                cancelAbilityInteraction(e);
                veteran.startBurst(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (manager.getAbility(p) instanceof FireAbility) {
            e.setCancelled(true);
        }
    }

    private boolean isFireStarter(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.FLINT_AND_STEEL
                || item.getType() == Material.FIRE_CHARGE;
    }

    private void cancelAbilityInteraction(PlayerInteractEvent e) {
        e.setCancelled(true);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
    }

    private boolean canUseSneakRightClick(Player p, Action action) {
        return p.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
    }

    private boolean canUseSneakLeftClick(Player p, Action action) {
        return p.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player p = e.getPlayer();
        if (!(manager.getAbility(p) instanceof ThePatriotAbility ha)) return;

        UUID u = p.getUniqueId();
        boolean prevGround = wasOnGround.getOrDefault(u, true);
        boolean currGround = p.isOnGround();
        wasOnGround.put(u, currGround);

        double y = p.getLocation().getY();
        if (!currGround && !p.isFlying()) fallPeakY.merge(u, y, Math::max);

        if (!prevGround && currGround) {
            if (fallPeakY.containsKey(u)) {
                double dist = fallPeakY.remove(u) - y;
                double min  = plugin.getConfig().getDouble(
                        "abilities.the_patriot.shared.fall_impact_height", 30);
                if (p.isSneaking() && dist >= min) ha.triggerFallImpact(p);
            }
            if (p.getFlySpeed() > 0.15f) p.setFlySpeed(0.1f);
        }

        if (!ha.isLaunching(p) && prevGround && !currGround
                && p.getVelocity().getY() > 0.15
                && p.isSneaking()
                && p.getLocation().getPitch() < -40f) {
            ha.tryLaunch(p);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!de.thomasugh.compoundv.ability.shared.BaseHeatVisionAbility.shouldCookDrops(e.getEntity())) return;

        for (ItemStack drop : e.getDrops()) {
            Material cooked = cookedVariant(drop.getType());
            if (cooked != null) drop.setType(cooked);
        }
    }

    private Material cookedVariant(Material material) {
        return switch (material) {
            case BEEF -> Material.COOKED_BEEF;
            case PORKCHOP -> Material.COOKED_PORKCHOP;
            case CHICKEN -> Material.COOKED_CHICKEN;
            case MUTTON -> Material.COOKED_MUTTON;
            case RABBIT -> Material.COOKED_RABBIT;
            case COD -> Material.COOKED_COD;
            case SALMON -> Material.COOKED_SALMON;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && manager.isThePatriot(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVeteranMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(manager.getAbility(attacker) instanceof TheVeteranAbility)) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        if (e.getDamager() instanceof Projectile) return;

        double horizontal = plugin.getConfig().getDouble("abilities.the_veteran.melee_knockback_horizontal", 1.35);
        double vertical = plugin.getConfig().getDouble("abilities.the_veteran.melee_knockback_vertical", 0.28);
        if (horizontal <= 0 && vertical <= 0) return;

        Vector dir = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) dir = attacker.getLocation().getDirection().clone();
        dir.setY(0).normalize().multiply(horizontal).setY(vertical);
        target.setVelocity(target.getVelocity().add(dir));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (manager.isThePatriot(p) && e.getDamager() instanceof Projectile) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.removeAbility(e.getPlayer());
        wasOnGround.remove(e.getPlayer().getUniqueId());
        fallPeakY.remove(e.getPlayer().getUniqueId());
    }
}
