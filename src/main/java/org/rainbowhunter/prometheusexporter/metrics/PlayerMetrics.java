package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class PlayerMetrics implements MetricGroup {

    private static final GameMode[] GAMEMODES = GameMode.values();

    private final boolean enabled;
    private final boolean cfgPing, cfgHealth, cfgFood, cfgSaturation,
                          cfgXp, cfgFlying, cfgGamemode;

    private Gauge playerPingGauge;
    private Gauge playerHealthGauge;
    private Gauge playerMaxHealthGauge;
    private Gauge playerFoodLevelGauge;
    private Gauge playerSaturationGauge;
    private Gauge playerXpLevelGauge;
    private Gauge playerXpProgressGauge;
    private Gauge playerTotalExperienceGauge;
    private Gauge playerFlyingGauge;
    private Gauge playerGamemodeGauge;

    public PlayerMetrics(FileConfiguration cfg) {
        enabled         = cfg.getBoolean("player_metrics.enabled",    true);
        cfgPing         = cfg.getBoolean("player_metrics.ping",       true);
        cfgHealth       = cfg.getBoolean("player_metrics.health",     true);
        cfgFood         = cfg.getBoolean("player_metrics.food",       true);
        cfgSaturation   = cfg.getBoolean("player_metrics.saturation", true);
        cfgXp           = cfg.getBoolean("player_metrics.xp",         true);
        cfgFlying       = cfg.getBoolean("player_metrics.flying",     true);
        cfgGamemode     = cfg.getBoolean("player_metrics.gamemode",   true);
    }

    @Override
    public void register() {
        if (!enabled) return;

        if (cfgPing) {
            playerPingGauge = Gauge.builder()
                    .name("mc_player_ping_milliseconds")
                    .help("Player latency in milliseconds")
                    .labelNames("player")
                    .register();
        }
        if (cfgHealth) {
            playerHealthGauge = Gauge.builder()
                    .name("mc_player_health")
                    .help("Player health")
                    .labelNames("player")
                    .register();
            playerMaxHealthGauge = Gauge.builder()
                    .name("mc_player_max_health")
                    .help("Player max health (useful when custom attributes are applied)")
                    .labelNames("player")
                    .register();
        }
        if (cfgFood) {
            playerFoodLevelGauge = Gauge.builder()
                    .name("mc_player_food_level")
                    .help("Hunger level per player (0-20)")
                    .labelNames("player")
                    .register();
        }
        if (cfgSaturation) {
            playerSaturationGauge = Gauge.builder()
                    .name("mc_player_saturation")
                    .help("Saturation per player (0.0-20.0+)")
                    .labelNames("player")
                    .register();
        }
        if (cfgXp) {
            playerXpLevelGauge = Gauge.builder()
                    .name("mc_player_xp_level")
                    .help("Player XP level")
                    .labelNames("player")
                    .register();
            playerXpProgressGauge = Gauge.builder()
                    .name("mc_player_xp_progress")
                    .help("Fractional XP progress within current level (0.0-1.0)")
                    .labelNames("player")
                    .register();
            playerTotalExperienceGauge = Gauge.builder()
                    .name("mc_player_total_experience")
                    .help("Total accumulated XP per player")
                    .labelNames("player")
                    .register();
        }
        if (cfgFlying) {
            playerFlyingGauge = Gauge.builder()
                    .name("mc_player_flying")
                    .help("1 if the player is flying, 0 otherwise")
                    .labelNames("player")
                    .register();
        }
        if (cfgGamemode) {
            playerGamemodeGauge = Gauge.builder()
                    .name("mc_player_gamemode")
                    .help("Player active gamemode (1 = active, 0 = inactive)")
                    .labelNames("player", "gamemode")
                    .register();
        }
    }

    @Override
    public void unregister() {
        playerPingGauge            = clear(playerPingGauge);
        playerHealthGauge          = clear(playerHealthGauge);
        playerMaxHealthGauge       = clear(playerMaxHealthGauge);
        playerFoodLevelGauge       = clear(playerFoodLevelGauge);
        playerSaturationGauge      = clear(playerSaturationGauge);
        playerXpLevelGauge         = clear(playerXpLevelGauge);
        playerXpProgressGauge      = clear(playerXpProgressGauge);
        playerTotalExperienceGauge = clear(playerTotalExperienceGauge);
        playerFlyingGauge          = clear(playerFlyingGauge);
        playerGamemodeGauge        = clear(playerGamemodeGauge);
    }

    @Override
    public void collect() {
        if (playerPingGauge == null && playerHealthGauge == null && playerMaxHealthGauge == null
                && playerFoodLevelGauge == null && playerSaturationGauge == null
                && playerXpLevelGauge == null && playerXpProgressGauge == null
                && playerTotalExperienceGauge == null && playerFlyingGauge == null
                && playerGamemodeGauge == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (playerPingGauge            != null) playerPingGauge.labelValues(name).set(player.getPing());
            if (playerHealthGauge          != null) playerHealthGauge.labelValues(name).set(player.getHealth());
            if (playerMaxHealthGauge != null) {
                AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) playerMaxHealthGauge.labelValues(name).set(maxHealth.getValue());
            }
            if (playerFoodLevelGauge       != null) playerFoodLevelGauge.labelValues(name).set(player.getFoodLevel());
            if (playerSaturationGauge      != null) playerSaturationGauge.labelValues(name).set(player.getSaturation());
            if (playerXpLevelGauge         != null) playerXpLevelGauge.labelValues(name).set(player.getLevel());
            if (playerXpProgressGauge      != null) playerXpProgressGauge.labelValues(name).set(player.getExp());
            if (playerTotalExperienceGauge != null) playerTotalExperienceGauge.labelValues(name).set(player.getTotalExperience());
            if (playerFlyingGauge          != null) playerFlyingGauge.labelValues(name).set(player.isFlying() ? 1 : 0);

            if (playerGamemodeGauge != null) {
                GameMode active = player.getGameMode();
                for (GameMode mode : GAMEMODES) {
                    playerGamemodeGauge.labelValues(name, mode.name()).set(mode == active ? 1 : 0);
                }
            }
        }
    }

    private static Gauge clear(Gauge g) {
        if (g != null) PrometheusRegistry.defaultRegistry.unregister(g);
        return null;
    }
}
