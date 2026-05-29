package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.ability.compoundv.FireAbility;
import de.thomasugh.compoundv.ability.compoundv.FireSonicAbility;
import de.thomasugh.compoundv.ability.compoundv.IceCubeAbility;
import de.thomasugh.compoundv.ability.compoundv.TheDetonatorAbility;
import de.thomasugh.compoundv.ability.compoundv.ToxicCloudAbility;
import de.thomasugh.compoundv.ability.compoundv.TheCountessAbility;
import de.thomasugh.compoundv.ability.compoundv.TheWarriorAbility;
import de.thomasugh.compoundv.ability.compoundv.TheHeadpopperAbility;
import de.thomasugh.compoundv.ability.compoundv.SpiderWeaverAbility;
import de.thomasugh.compoundv.ability.compoundv.FlyAbility;
import de.thomasugh.compoundv.ability.compoundv.TheGhostAbility;
import de.thomasugh.compoundv.ability.compoundv.JumperAbility;
import de.thomasugh.compoundv.ability.compoundv.FlashLightAbility;
import de.thomasugh.compoundv.ability.compoundv.TheDiverAbility;
import de.thomasugh.compoundv.ability.compoundv.TheRunnerAbility;
import de.thomasugh.compoundv.ability.compoundv.VisionAbility;
import de.thomasugh.compoundv.ability.shared.ThePatriotAbility;
import de.thomasugh.compoundv.ability.vone.SonicBoomAbility;
import de.thomasugh.compoundv.ability.vone.SizeChangerAbility;
import de.thomasugh.compoundv.ability.vone.TheVeteranAbility;
import de.thomasugh.compoundv.ability.vone.StormstrikeAbility;
import de.thomasugh.compoundv.ability.vone.HealAngelAbility;
import de.thomasugh.compoundv.ability.vone.SubmarineAbility;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.manager.PotionRollManager;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerActionListener implements Listener {

    private final CompoundV   plugin;
    private final AbilityManager    manager;
    private final PotionRollManager roller;

    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final Map<UUID, Double>  fallPeakY   = new HashMap<>();

    public PlayerActionListener(CompoundV plugin, AbilityManager manager, PotionRollManager roller) {
        this.plugin = plugin; this.manager = manager; this.roller = roller;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (e.isCancelled()) return;
        CompoundPotion type = ItemUtil.getBottleType(e.getItem());
        if (type == null) {
            if (e.getItem() != null && e.getItem().getType() == Material.MILK_BUCKET && manager.hasAbility(e.getPlayer())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(plugin.getLocaleManager().msg("potion.milk_blocked"));
            }
            return;
        }

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
            p.addPotionEffect(new PotionEffect(PotionEffects.NAUSEA, 100, 0, false, true));
            return;
        }

        if (type == CompoundPotion.V_NULL) {
            p.sendMessage(plugin.getLocaleManager().msg("potion.drink_v_null"));
            plugin.getSideEffectManager().applyVNull(p);
            return;
        }

        PlayerAbilityData current = manager.getData(p);
        if (current != null && current.isExpired()) {
            manager.removeAndForget(p);
            current = null;
        }

        if (current != null) {
            if (handlePotionStackingRules(p, type, current)) {
                return;
            }
        }

        playDrinkPreparation(p, type);
        if (roller.roll(p, type)) {
            plugin.getSideEffectManager().afterSuccessfulRoll(p, type);
        }
    }

    private boolean handlePotionStackingRules(Player p, CompoundPotion type, PlayerAbilityData current) {
        CompoundPotion activeType = current.potionType();

        if (activeType == CompoundPotion.V_ONE && (type == CompoundPotion.COMPOUND_V || type == CompoundPotion.TEMP_V || type == CompoundPotion.V_ONE)) {
            p.sendMessage(plugin.getLocaleManager().msg("potion.no_effect_v_one_active"));
            return true;
        }

        if (activeType == CompoundPotion.TEMP_V) {
            if (type == CompoundPotion.TEMP_V) {
                if (roller.extendTemporaryAbility(p, CompoundPotion.TEMP_V)) {
                    plugin.getSideEffectManager().afterSuccessfulRoll(p, CompoundPotion.TEMP_V);
                    p.sendMessage(plugin.getLocaleManager().msg("potion.temp_v_extended"));
                } else if (roller.roll(p, CompoundPotion.TEMP_V)) {
                    plugin.getSideEffectManager().afterSuccessfulRoll(p, CompoundPotion.TEMP_V);
                }
                return true;
            }

            if (type == CompoundPotion.COMPOUND_V) {
                scheduleCompoundVAfterTemp(p, current);
                plugin.getSideEffectManager().afterSuccessfulRoll(p, CompoundPotion.COMPOUND_V);
                p.sendMessage(plugin.getLocaleManager().msg("potion.compound_v_after_temp"));
                return true;
            }
        }

        if (activeType == CompoundPotion.COMPOUND_V) {
            if (type == CompoundPotion.COMPOUND_V) {
                p.sendMessage(plugin.getLocaleManager().msg("potion.no_effect_compound_active"));
                return true;
            }

            if (type == CompoundPotion.TEMP_V) {
                plugin.getSideEffectManager().applyMinorCompatibility(p);
                p.sendMessage(plugin.getLocaleManager().msg("potion.temp_v_blocked_by_compound"));
                return true;
            }

            if (type == CompoundPotion.V_ONE) {
                String upgrade = vOneUpgradeFor(current.abilityId());
                if (upgrade == null) {
                    p.sendMessage(plugin.getLocaleManager().msg("potion.v_one_no_upgrade"));
                    return true;
                }
                playDrinkPreparation(p, CompoundPotion.V_ONE);
                manager.giveAbility(p, upgrade, CompoundPotion.V_ONE, 0L);
                plugin.getSideEffectManager().afterSuccessfulRoll(p, CompoundPotion.V_ONE);
                p.sendMessage(plugin.getLocaleManager().msg("potion.v_one_upgrade"));
                return true;
            }
        }

        return false;
    }

    private String vOneUpgradeFor(String abilityId) {
        if ("the_patriot".equalsIgnoreCase(abilityId)) return "the_patriot_v_one";
        if ("teleporter".equalsIgnoreCase(abilityId)) return "teleporter_v_one";
        if ("size_changer".equalsIgnoreCase(abilityId)) return "size_changer_v_one";
        return null;
    }

    private void playDrinkPreparation(Player p, CompoundPotion type) {
        if (type == CompoundPotion.V_ONE) {
            int ticks = plugin.getConfig().getInt("v_one.drink_effect_seconds", 5) * 20;
            p.addPotionEffect(new PotionEffect(PotionEffects.BLINDNESS, ticks, 0, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffects.WEAKNESS,  ticks, 2, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS,  ticks, 2, false, true));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.8f, 0.6f);
            p.sendMessage(plugin.getLocaleManager().msg("potion.drink_v_one"));
        } else if (type == CompoundPotion.TEMP_V) {
            p.sendMessage(plugin.getLocaleManager().msg("potion.drink_temp_v"));
        } else {
            p.sendMessage(plugin.getLocaleManager().msg("potion.drinking"));
        }
    }

    private void scheduleCompoundVAfterTemp(Player player, PlayerAbilityData tempData) {
        long delayTicks = Math.max(2L, tempData.remainingSec() * 20L + 2L);
        UUID uuid = player.getUniqueId();
        String tempAbilityId = tempData.abilityId();
        long tempExpiresAt = tempData.expiresAt();

        SchedulerAdapter.runLater(plugin, player, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;

            PlayerAbilityData now = manager.getData(p);
            if (now != null && now.potionType() == CompoundPotion.TEMP_V
                    && now.abilityId().equalsIgnoreCase(tempAbilityId)
                    && now.expiresAt() == tempExpiresAt) {
                manager.removeAndForget(p);
            }

            if (!manager.hasAbility(p)) {
                if (roller.roll(p, CompoundPotion.COMPOUND_V)) {
                    p.sendMessage(plugin.getLocaleManager().msg("potion.compound_v_delayed_granted"));
                }
            }
        }, delayTicks);
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


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownPotion potion)) return;
        CompoundPotion type = ItemUtil.getAnyBottleType(potion.getItem());
        if (type == null) return;
        if (type == CompoundPotion.V_NULL) return;

        e.setCancelled(true);
        if (potion.getShooter() instanceof Player player) {
            player.sendMessage(plugin.getLocaleManager().msg("potion.only_drinkable"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (ItemUtil.getAnyBottleType(potion.getItem()) != CompoundPotion.V_NULL) return;

        event.setCancelled(true);
        double radius = plugin.getConfig().getDouble("v_null.splash.radius", 4.0);
        potion.getWorld().playSound(potion.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 0.9f, 0.65f);
        potion.getWorld().spawnParticle(Particle.WITCH, potion.getLocation(), 55, radius * 0.35, 0.45, radius * 0.35, 0.08);
        potion.getWorld().spawnParticle(Particle.SMOKE, potion.getLocation(), 35, radius * 0.30, 0.30, radius * 0.30, 0.03);
        for (org.bukkit.entity.Entity nearby : potion.getWorld().getNearbyEntities(potion.getLocation(), radius, radius, radius)) {
            if (nearby instanceof LivingEntity entity) {
                plugin.getSideEffectManager().applyVNull(entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) {
        ThrownPotion potion = event.getEntity();
        if (ItemUtil.getAnyBottleType(potion.getItem()) != CompoundPotion.V_NULL) return;

        AreaEffectCloud cloud = event.getAreaEffectCloud();
        double radius = plugin.getConfig().getDouble("v_null.lingering.radius", cloud != null ? Math.max(3.0, cloud.getRadius()) : 4.0);
        int durationTicks = Math.max(20, plugin.getConfig().getInt("v_null.lingering.duration_ticks", 160));
        if (cloud != null) {
            cloud.setRadius((float) radius);
            cloud.setDuration(durationTicks);
            cloud.setRadiusPerTick(0.0f);
            cloud.setParticle(Particle.WITCH);
        }
        potion.getWorld().playSound(potion.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 0.9f, 0.55f);
        final Location origin = potion.getLocation().clone();
        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimerAt(plugin, origin, new Runnable() {
            int age = 0;
            @Override public void run() {
                if (age >= durationTicks) {
                    if (task[0] != null) task[0].cancel();
                    return;
                }
                origin.getWorld().spawnParticle(Particle.WITCH, origin, 18, radius * 0.35, 0.35, radius * 0.35, 0.04);
                for (org.bukkit.entity.Entity nearby : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
                    if (nearby instanceof LivingEntity entity) {
                        plugin.getSideEffectManager().applyVNull(entity);
                    }
                }
                age += 10;
            }
        }, 0L, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        Ability ability = manager.getAbility(player);
        if (ability instanceof ThePatriotAbility patriot && patriot.trySpeedDash(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Ability ab = manager.getAbility(e.getPlayer());
        if (ab instanceof IceCubeAbility iceCube) {
            e.setCancelled(true);
            iceCube.shootIceSpike(e.getPlayer());
            return;
        }
        if (ab instanceof SonicBoomAbility sonic) {
            e.setCancelled(true);
            sonic.triggerSonicRing(e.getPlayer());
            return;
        }
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

        CompoundPotion throwableType = ItemUtil.getAnyBottleType(e.getItem());
        if (ItemUtil.isThrowableCompoundVBottle(e.getItem()) && throwableType != CompoundPotion.V_NULL) {
            cancelAbilityInteraction(e);
            p.sendMessage(plugin.getLocaleManager().msg("potion.only_drinkable"));
            return;
        }

        if (ItemUtil.isCompoundVBottle(p.getInventory().getItemInMainHand())) return;
        if (ItemUtil.isCompoundVBottle(p.getInventory().getItemInOffHand()))  return;

        Ability ab = manager.getAbility(p);
        if (ab == null) return;

        if (ab instanceof FireAbility && isFireStarter(e.getItem())) {
            cancelAbilityInteraction(e);
            return;
        }

        if (e.getHand() != EquipmentSlot.HAND) return;

        if (ab instanceof IceCubeAbility iceCube && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            iceCube.createIceWall(p);
            return;
        }

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

        if (ab instanceof SubmarineAbility submarine) {
            if (canUseSneakLeftClick(p, a)) {
                cancelAbilityInteraction(e);
                submarine.toggleSonar(p);
                return;
            }
            if (canUseSneakRightClick(p, a)) {
                cancelAbilityInteraction(e);
                submarine.useRiptide(p);
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

        if (ab instanceof SonicBoomAbility sonic && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            sonic.fireSonicBeam(p);
            return;
        }

        if (ab instanceof SizeChangerAbility sizeChanger && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            sizeChanger.handleSneakLeftClick(p);
            return;
        }

        if (ab instanceof StormstrikeAbility stormstrike && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            stormstrike.strikeLightning(p);
            return;
        }

        if (ab instanceof FlashLightAbility flashLight && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            flashLight.fireLightBeam(p);
            return;
        }

        if (ab instanceof ToxicCloudAbility toxicCloud && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            toxicCloud.shootVomit(p);
            return;
        }

        if (ab instanceof TheHeadpopperAbility headpopper && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            headpopper.markTarget(p);
            return;
        }

        if (ab instanceof SpiderWeaverAbility spiderWeaver && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            spiderWeaver.shootWeb(p);
            return;
        }

        if (ab instanceof TheDetonatorAbility detonator && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            detonator.triggerExplosion(p);
            return;
        }


        if (ab instanceof TheVeteranAbility veteran && canUseSneakLeftClickAny(p, a)) {
            cancelAbilityInteraction(e);
            veteran.handleSneakLeftClick(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Ability ability = manager.getAbility(player);
        if (ability == null) return;

        boolean flightAbility = "fly".equalsIgnoreCase(ability.getId())
                || ability instanceof ThePatriotAbility
                || ability instanceof SonicBoomAbility
                || ability instanceof StormstrikeAbility
                || ability instanceof TheCountessAbility
                || ability instanceof TheWarriorAbility;
        if (!flightAbility) return;

        SchedulerAdapter.runLater(plugin, player, () -> {
            Ability current = manager.getAbility(player);
            if (!player.isOnline() || current == null) return;
            if (!current.getId().equalsIgnoreCase(ability.getId())) return;
            player.setAllowFlight(true);
        }, 1L);
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
        return p.isSneaking() && action == Action.LEFT_CLICK_AIR;
    }

    private boolean canUseSneakLeftClickAny(Player p, Action action) {
        return p.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent e) {
        Player player = e.getPlayer();
        if (!player.isSneaking()) return;
        Ability ability = manager.getAbility(player);
        if (ability instanceof SizeChangerAbility sizeChanger) {
            sizeChanger.handleSneakLeftClick(player);
            return;
        }
        if (ability instanceof StormstrikeAbility stormstrike) {
            stormstrike.strikeLightning(player);
            return;
        }
        if (ability instanceof FlashLightAbility flashLight) {
            flashLight.fireLightBeam(player);
            return;
        }
        if (ability instanceof ToxicCloudAbility toxicCloud) {
            toxicCloud.shootVomit(player);
            return;
        }
        if (ability instanceof TheHeadpopperAbility headpopper) {
            headpopper.markTarget(player);
            return;
        }
        if (ability instanceof SpiderWeaverAbility spiderWeaver) {
            spiderWeaver.shootWeb(player);
            return;
        }
        if (ability instanceof TheDetonatorAbility detonator) {
            detonator.triggerExplosion(player);
            return;
        }
        if (ability instanceof TheVeteranAbility veteran && !veteran.isBurstActive(player)) {
            veteran.handleSneakLeftClick(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Ability ability = manager.getAbility(player);
        if (ability instanceof TheCountessAbility countess) {
            event.setCancelled(true);
            countess.tryDoubleJump(player);
            return;
        }
        if (ability instanceof TheWarriorAbility warrior) {
            event.setCancelled(true);
            warrior.tryDoubleJump(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!hasChangedPosition(e)) return;
        Player p = e.getPlayer();
        Ability ability = manager.getAbility(p);
        if (ability instanceof TheRunnerAbility runner) {
            runner.handleMoveThroughEntities(p, e.getFrom(), e.getTo());
        }

        if (!(ability instanceof ThePatriotAbility)
                && !(ability instanceof FlyAbility)
                && !(ability instanceof SonicBoomAbility)
                && !(ability instanceof StormstrikeAbility)) {
            return;
        }

        UUID u = p.getUniqueId();
        boolean prevGround = wasOnGround.getOrDefault(u, true);
        boolean currGround = p.isOnGround();
        wasOnGround.put(u, currGround);

        if (ability instanceof ThePatriotAbility patriot) {
            double y = p.getLocation().getY();
            if (!currGround && !p.isFlying()) fallPeakY.merge(u, y, Math::max);

            if (!prevGround && currGround && fallPeakY.containsKey(u)) {
                double trackedDistance = fallPeakY.remove(u) - y;
                double dist = Math.max(trackedDistance, p.getFallDistance());
                double particleMin = plugin.getConfig().getDouble(
                        "abilities.the_patriot.shared.fall_impact_particle_height", 10);
                double blockMin = plugin.getConfig().getDouble(
                        "abilities.the_patriot.shared.fall_impact_block_height",
                        plugin.getConfig().getDouble("abilities.the_patriot.shared.fall_impact_height", 40));
                double min = Math.min(particleMin, blockMin);
                if (p.isSneaking() && dist >= min) patriot.triggerFallImpact(p, dist);
            }
        }

        if (ability instanceof SonicBoomAbility sonic) {
            double y = p.getLocation().getY();
            if (!currGround && !p.isFlying()) fallPeakY.merge(u, y, Math::max);

            if (!prevGround && currGround && fallPeakY.containsKey(u)) {
                double trackedDistance = fallPeakY.remove(u) - y;
                double dist = Math.max(trackedDistance, p.getFallDistance());
                double particleMin = plugin.getConfig().getDouble(
                        "abilities.sonic_boom.fall_impact_particle_height", 10);
                double blockMin = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_block_height", 35);
                double min = Math.min(particleMin, blockMin);
                if (p.isSneaking() && dist >= min) sonic.triggerFallImpact(p, dist);
            }
        }

        if (ability instanceof StormstrikeAbility stormstrike) {
            double y = p.getLocation().getY();
            if (!currGround && !p.isFlying()) fallPeakY.merge(u, y, Math::max);

            if (!prevGround && currGround && fallPeakY.containsKey(u)) {
                double trackedDistance = fallPeakY.remove(u) - y;
                double dist = Math.max(trackedDistance, p.getFallDistance());
                double min = plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_min_blocks", 10.0);
                if (p.isSneaking() && dist >= min) stormstrike.triggerFallImpact(p, dist);
            }
        }

        if (!prevGround && currGround && p.getFlySpeed() > 0.1001f) {
            p.setFlySpeed(0.1f);
        }


        if (!prevGround || currGround
                || p.getVelocity().getY() <= 0.15
                || !p.isSneaking()
                || p.getLocation().getPitch() >= -40f) {
            return;
        }

        if (ability instanceof ThePatriotAbility patriot && !patriot.isLaunching(p)) {
            patriot.tryLaunch(p);
        } else if (ability instanceof FlyAbility fly && !fly.isLaunching(p)) {
            fly.tryLaunch(p);
        } else if (ability instanceof SonicBoomAbility sonic && !sonic.isLaunching(p)) {
            sonic.tryLaunch(p);
        } else if (ability instanceof StormstrikeAbility stormstrike && !stormstrike.isLaunching(p)) {
            stormstrike.tryLaunch(p);
        }
    }

    private boolean hasChangedPosition(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        for (Ability ability : plugin.getRegistry().all()) {
            if (ability instanceof IceCubeAbility iceCube && iceCube.handleProjectileHit(event)) {
                return;
            }
        }
        for (Ability ability : plugin.getRegistry().all()) {
            if (ability instanceof TheCountessAbility countess && countess.isOwnedFireball(event.getEntity().getUniqueId())) {
                countess.handleProjectileHit(event);
                return;
            }
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
    public void onMobTargetInvisiblePlayer(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (!(manager.getAbility(player) instanceof TheGhostAbility ghost)) return;
        if (!ghost.hidesFromMobs() || !ghost.isInvisible(player)) return;
        if (event.getEntity() instanceof Mob) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHealAngelCommandedMobTargetsOwner(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player owner)) return;
        if (!(manager.getAbility(owner) instanceof HealAngelAbility healAngel)) return;
        if (!healAngel.isCommandedByOwner(event.getEntity(), owner)) return;
        event.setCancelled(true);
        event.setTarget(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHealAngelCommandedMobDamagesOwner(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        if (!(event.getDamager() instanceof Mob mob)) return;
        if (!(manager.getAbility(owner) instanceof HealAngelAbility healAngel)) return;
        if (!healAngel.isCommandedByOwner(mob, owner)) return;
        event.setCancelled(true);
        mob.setTarget(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Ability currentAbility = manager.getAbility(p);
        if (currentAbility instanceof TheDetonatorAbility detonator
                && detonator.shouldCancelSelfExplosionDamage(p)
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            e.setCancelled(true);
            return;
        }
        if (currentAbility instanceof StormstrikeAbility
                && e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Ability ability = currentAbility;
            if (ability instanceof TheWarriorAbility warrior) {
                e.setCancelled(true);
                if (p.isSneaking()) warrior.triggerFallImpact(p, p.getFallDistance());
                return;
            }
            if (ability instanceof SpiderWeaverAbility || ability instanceof StormstrikeAbility) {
                e.setCancelled(true);
                return;
            }
            if (manager.isThePatriot(p)
                    || ability instanceof SonicBoomAbility
                    || (ability instanceof JumperAbility jumper && jumper.preventsFallDamage(p))) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElementalMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        Ability ability = manager.getAbility(attacker);
        if (ability instanceof HealAngelAbility healAngel && healAngel.handleHealingHit(attacker, target)) {
            e.setDamage(0.0);
            e.setCancelled(true);
            return;
        }
        if (ability instanceof FireSonicAbility fireSonic) {
            fireSonic.handleMeleeHit(attacker, target);
        } else if (ability instanceof TheCountessAbility countess) {
            countess.handleMeleeHit(attacker, target);
        } else if (ability instanceof TheDetonatorAbility detonator) {
            detonator.handleMeleeHit(attacker, target);
        }
        if (ability instanceof IceCubeAbility iceCube) {
            iceCube.handleMeleeHit(attacker, target);
        }
        if (ability instanceof ToxicCloudAbility toxicCloud) {
            toxicCloud.handleMeleeHit(attacker, target);
        }
        if (ability instanceof StormstrikeAbility stormstrike) {
            stormstrike.handleMeleeHit(attacker, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShockwaveMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(manager.getAbility(attacker) instanceof de.thomasugh.compoundv.ability.compoundv.ShockwaveAbility shockwave)) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        shockwave.handleMeleeHit(attacker, target);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSizeChangerMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(manager.getAbility(attacker) instanceof SizeChangerAbility sizeChanger)) return;
        if (!sizeChanger.isBig(attacker)) return;
        double multiplier = sizeChanger.bigDamageMultiplier();
        e.setDamage(e.getDamage() * Math.max(0.0, multiplier));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSonicBoomMeleeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(manager.getAbility(attacker) instanceof SonicBoomAbility sonic)) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        sonic.handleMeleeHit(attacker, target, e.getDamage(), e::setDamage);
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
    public void onSonicBoomFallExplosionPlayerDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(manager.getAbility(attacker) instanceof SonicBoomAbility sonic)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        if (!sonic.shouldReduceFallExplosionPlayerDamage(attacker)) return;

        double multiplier = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_player_damage_multiplier", 0.8);
        e.setDamage(e.getDamage() * Math.max(0.0, multiplier));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamageInvisiblePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(manager.getAbility(player) instanceof TheGhostAbility ghost)) return;
        if (!ghost.hidesFromMobs() || !ghost.isInvisible(player)) return;

        if (event.getDamager() instanceof Mob) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Mob) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if ((manager.isThePatriot(p) || manager.getAbility(p) instanceof SonicBoomAbility)
                && e.getDamager() instanceof Projectile) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.removeAbility(e.getPlayer());
        wasOnGround.remove(e.getPlayer().getUniqueId());
        fallPeakY.remove(e.getPlayer().getUniqueId());
    }
}
