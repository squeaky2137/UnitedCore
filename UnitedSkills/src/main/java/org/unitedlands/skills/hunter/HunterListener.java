package org.unitedlands.skills.hunter;

import com.destroystokyo.paper.ParticleBuilder;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.unitedlands.skills.UnitedSkills;
import org.unitedlands.skills.skill.ActiveSkill;
import org.unitedlands.skills.skill.Skill;
import org.unitedlands.skills.skill.SkillType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class HunterListener implements Listener {
    private final UnitedSkills unitedSkills;
    private Player player;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> durations = new HashMap<>();
    private final Collection<LivingEntity> bleedingEntities = new ArrayList<>();
    private final HashMap<LivingEntity, Long> bleedingDurations = new HashMap<>();

    public HunterListener(UnitedSkills unitedSkills) {
        this.unitedSkills = unitedSkills;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        bleedingEntities.remove(entity);

        if (entity.getKiller() == null) {
            return;
        }
        player = entity.getKiller();
        if (!isHunter()) {
            return;
        }
        if (isSpawnerMob(entity)) {
            return;
        }

        Skill selfReflection = new Skill(player, SkillType.SELF_REFLECTION);
        if (selfReflection.getLevel() == 0) {
            return;
        }
        int xp = event.getDroppedExp();
        xp = (int) (xp * (1 + (selfReflection.getLevel() * 0.1)));
        event.setDroppedExp(xp);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        player = (Player) event.getEntity();
        if (!isHunter()) {
            return;
        }
        Skill counterAttack = new Skill(player, SkillType.COUNTER_ATTACK);
        if (counterAttack.getLevel() == 0) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Entity entity = event.getDamager();
        if (counterAttack.isSuccessful()) {
            event.setCancelled(true);
            double damage = event.getDamage() / 2;
            if (entity instanceof Player) {
                return;
            }
            if (player.getHealth() == 1) {
                return;
            }
            if (entity instanceof LivingEntity) {
                spawnBleedingParticles(entity);
                ((LivingEntity) entity).damage(damage);
                counterAttack.notifyActivation();
            }
        }
    }

    @EventHandler
    public void onPrecisionStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        player = (Player) event.getDamager();
        if (!isHunter()) {
            return;
        }
        ActiveSkill precisionStrike = new ActiveSkill(player, SkillType.PRECISION_STRIKE, cooldowns, durations);
        if (precisionStrike.getLevel() == 0) {
            return;
        }
        if (!precisionStrike.isActive()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity entity) {
            if (bleedingEntities.contains(entity)) {
                return;
            }
            bleedingEntities.add(entity);
            int bleedingTimeInSeconds = 15;
            bleedingDurations.put(entity, System.currentTimeMillis() + (bleedingTimeInSeconds * 1000L));
        }
    }

    public void damageBleedingEntities() {
        unitedSkills.getServer().getScheduler().runTaskTimer(unitedSkills, task -> {
            if (bleedingEntities.isEmpty()) {
                return;
            }
            for (LivingEntity entity : bleedingEntities) {
                if (((bleedingDurations.get(entity) - System.currentTimeMillis()) / 1000L) <= 0) {
                    bleedingEntities.remove(entity);
                    bleedingDurations.remove(entity);
                    continue;
                }
                spawnBleedingParticles(entity);
                entity.damage(2);
            }
        },0, 20);
    }

    @EventHandler
    public void onPrecisionActivation(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        Material material = event.getItem().getType();
        SkillType skillType;
        if (material.toString().contains("SWORD") || material.toString().contains("AXE")) {
            skillType = SkillType.PRECISION_STRIKE;
        } else if (material.equals(Material.BOW)) {
            skillType = SkillType.FOCUS;
        } else {
            return;
        }
        player = event.getPlayer();
        if (!isHunter()) {
            return;
        }
        ActiveSkill skill = new ActiveSkill(player, skillType, cooldowns, durations);
        if (skill.getLevel() == 0) {
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        skill.activate();
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        player = shooter;
        if (!isHunter()) {
            return;
        }
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        Skill skill = new Skill(player, SkillType.RETRIEVER);
        if (skill.getLevel() == 0) {
            return;
        }
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (bow.containsEnchantment(Enchantment.ARROW_INFINITE)) {
            if (arrow.getBasePotionData().getType() != PotionType.UNCRAFTABLE){
                if (skill.isSuccessful()) {
                    arrow.setMetadata("retrieved", new FixedMetadataValue(unitedSkills, true));
                }
            }
        } else {
            if (skill.isSuccessful()) {
                arrow.setMetadata("retrieved", new FixedMetadataValue(unitedSkills, true));
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Bukkit.getScheduler().runTask(unitedSkills, () -> {
            if (event.getEntity().hasMetadata("retrieved")) {
                Arrow arrow = (Arrow) event.getEntity();
                if (arrow.getShooter() instanceof Player shooter) {
                    shooter.getInventory().addItem(arrow.getItemStack().asOne());
                }
                event.getEntity().remove();
            }
        });
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (event.getHitEntity() instanceof Player) {
            return;
        }
        if (event.getHitBlock() != null) {
            return;
        }
        player = shooter;
        if (!isHunter()) {
            return;
        }
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        Skill piercing = new Skill(player, SkillType.PIERCING);
        if (piercing.isSuccessful()) {
            piercing.notifyActivation();
            int modifier = 1;
            if (piercing.getLevel() == 3) {
                modifier = 2;
            }
            arrow.setPierceLevel(arrow.getPierceLevel() + modifier);
        }
        Skill criticalHit = new Skill(player, SkillType.CRITICAL_HIT);
        if (criticalHit.isSuccessful()) {
            criticalHit.notifyActivation();
            arrow.setDamage(arrow.getDamage() * 2);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        player = damager;
        if (!isHunter()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        if (!(entity instanceof LivingEntity victim)) {
            return;
        }
        Skill stun = new Skill(player, SkillType.STUN);
        if (stun.isSuccessful()) {
            stun.notifyActivation();
            int level = stun.getLevel();
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, level * 20, 10));
        }
        Skill criticalHit = new Skill(player, SkillType.CRITICAL_HIT);
        if (criticalHit.isSuccessful()) {
            criticalHit.notifyActivation();
            event.setDamage(event.getDamage() * 2);
        }
        ActiveSkill focus = new ActiveSkill(player, SkillType.FOCUS, cooldowns, durations);
        if (focus.isActive()) {
            event.setDamage(event.getDamage() * (1 + (focus.getLevel() * 0.2)));
        }
    }

    private void spawnBleedingParticles(Entity entity) {
        ParticleBuilder particle = new ParticleBuilder(Particle.BLOCK_CRACK);
        BlockData redWoolData = Material.RED_WOOL.createBlockData();
        particle.data(redWoolData);
        particle.count(50);
        particle.location(entity.getLocation());
        particle.spawn();
    }

    @EventHandler
    public void onEntitySpawn(SpawnerSpawnEvent event) {
        event.getEntity().setMetadata("spawner-mob",
                new FixedMetadataValue(unitedSkills, "spawner-mob"));
    }

    private boolean isSpawnerMob(Entity entity) {
        return entity.hasMetadata("spawner-mob");
    }
    private boolean isHunter() {
        JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
        for (JobProgression job : jobsPlayer.getJobProgression()) {
            return job.getJob().getName().equals("Hunter");
        }
        return false;
    }
}
