package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.rainbowhunter.prometheusexporter.collector.LabeledGauge;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class PlayerMetrics extends MetricGroup {

    public PlayerMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot() { return "player_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(
            player("ping_milliseconds",  "mc_player_ping_milliseconds", "Player latency in milliseconds",                          Player::getPing),
            player("health",             "mc_player_health",            "Player health",                                           Player::getHealth),
            player("max_health",         "mc_player_max_health",        "Player max health (useful when custom attributes are applied)", p -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                return attr != null ? attr.getValue() : 0.0;
            }),
            player("food_level",         "mc_player_food_level",        "Hunger level per player (0-20)",                          Player::getFoodLevel),
            player("saturation",         "mc_player_saturation",        "Saturation per player (0.0-20.0+)",                       Player::getSaturation),
            player("xp_level",           "mc_player_xp_level",          "Player XP level",                                         Player::getLevel),
            player("xp_progress",        "mc_player_xp_progress",       "Fractional XP progress within current level (0.0-1.0)",   Player::getExp),
            player("total_experience",   "mc_player_total_experience",  "Total accumulated XP per player",                         Player::getTotalExperience),
            player("flying",             "mc_player_flying",            "1 if the player is flying, 0 otherwise",                  p -> p.isFlying() ? 1.0 : 0.0),
            player("gamemode",           "mc_player_gamemode",          "Player active gamemode (0=survival, 1=creative, 2=adventure, 3=spectator)", p -> p.getGameMode().getValue())
        );
    }

    private static MetricCollector player(String configKey, String name, String help, ToDoubleFunction<Player> fn) {
        return new LabeledGauge<>(configKey, name, help, "player",
                Bukkit::getOnlinePlayers, Player::getName, fn);
    }
}
