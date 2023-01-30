package org.unitedlands.wars.war.health;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.unitedlands.wars.UnitedWars;
import org.unitedlands.wars.war.WarDatabase;
import org.unitedlands.wars.war.entities.WarringEntity;

import java.time.Duration;

class Healer {
    private static final UnitedWars PLUGIN = UnitedWars.getInstance();
    private final WarHealth warHealth;
    private final BossBar regenBossbar;
    private long startTime;

    public Healer(WarHealth warHealth) {
        this.startTime = (long) (System.currentTimeMillis() + warHealth.getHealingRate() * 60_000L);
        this.warHealth = warHealth;
        this.regenBossbar = getRegenBossbar();
    }

    void start() {
        // No need to do anything if its already healing
        WarringEntity warringEntity = WarDatabase.getWarringEntity(warHealth);
        int delay = warHealth.getHealingRate() * 60;
        float decrease = (float) 1 / delay;

        PLUGIN.getServer().getScheduler().runTaskTimer(PLUGIN, task -> {
            if (isFull()) {
                hideTimer();
                return;
            }
            // Show the timer and set to healing
            showTimer();
            warHealth.setHealing(true);

            if (getRemainingSeconds() <= 0) {
                startTime = System.currentTimeMillis() + warHealth.getHealingRate() * 60_000L;
                warHealth.increaseHealth(1);
                warHealth.setHealing(false);
                warringEntity.getOnlinePlayers().forEach(p -> p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1, 1));
                // Recursive call until it fills up.
                if (!isFull()) {
                    regenBossbar.progress(1.0F); // Refill timer bar
                    start(); // Start healing again
                }
            }
            updateTimer(decrease);
        }, 0, 20L);
    }

    public long getStartTime() {
        return startTime;
    }
    private void updateTimer(float decrease) {
        System.out.println(decrease + " decrease");
        System.out.println(regenBossbar.progress() + " current prog");
        System.out.println(regenBossbar.progress() - decrease + " actual");
        Component name = Component.text("NEXT HEAL IN: ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD);
        regenBossbar.name(name.append(Component.text(getFormattedTime(), NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false)));
        regenBossbar.progress(Math.max(0.0F, regenBossbar.progress() - decrease));
    }

    private String getFormattedTime() {
        Duration duration = Duration.ofSeconds(getRemainingSeconds());
        return String.format("%02d:%02d", duration.toMinutes(), duration.toSecondsPart());
    }

    private int getRemainingSeconds() {
        long currentTimeStamp = System.currentTimeMillis();
        return (int) (Math.max(0, Math.floor(startTime - currentTimeStamp) / 1000));
    }

    @NotNull
    private BossBar getRegenBossbar() {
        Component time = Component.text(getFormattedTime(), NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false);
        final Component name = Component.text("NEXT HEAL IN: ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD);
        return BossBar.bossBar(name.append(time), 1, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    }

    public void showTimer(Player player) {
        if (isFull())
            return;
        player.showBossBar(this.regenBossbar);
    }

    public void hideTimer(Player player) {
        if (isFull())
            return;
        player.hideBossBar(this.regenBossbar);
    }

    private void showTimer() {
        if (isFull())
            return;
        WarringEntity warringEntity = WarDatabase.getWarringEntity(warHealth);
        warringEntity.getOnlinePlayers().forEach(p -> p.showBossBar(this.regenBossbar));
    }

    private boolean isFull() {
        return warHealth.getValue() == warHealth.getMaxHealth();
    }

    private void hideTimer() {
        WarringEntity warringEntity = WarDatabase.getWarringEntity(warHealth);
        warringEntity.getOnlinePlayers().forEach(p -> p.hideBossBar(this.regenBossbar));
    }

    public void setStartTime(long t) {
        this.startTime = t;
    }
}
