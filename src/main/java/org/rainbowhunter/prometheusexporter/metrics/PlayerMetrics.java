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
            player("ping_milliseconds", "Player latency in milliseconds",                          Player::getPing),
            player("health",            "Player health",                                           Player::getHealth),
            player("max_health",        "Player max health (useful when custom attributes are applied)", p -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                return attr != null ? attr.getValue() : 0.0;
            }),
            player("food_level",        "Hunger level per player (0-20)",                          Player::getFoodLevel),
            player("saturation",        "Saturation per player (0.0-20.0+)",                       Player::getSaturation),
            player("xp_level",          "Player XP level",                                         Player::getLevel),
            player("xp_progress",       "Fractional XP progress within current level (0.0-1.0)",   Player::getExp),
            player("total_experience",  "Total accumulated XP per player",                         Player::getTotalExperience),
            player("flying",            "1 if the player is flying, 0 otherwise",                  p -> p.isFlying() ? 1.0 : 0.0),
            player("gamemode",          "Player active gamemode (0=survival, 1=creative, 2=adventure, 3=spectator)", p -> p.getGameMode().getValue())
        );
    }

    private MetricCollector player(String metric, String help, ToDoubleFunction<Player> fn) {
        String name = prefix + "player_" + metric;
        return new LabeledGauge<>(metric, name, help, "player",
                Bukkit::getOnlinePlayers, Player::getName, fn);
    }
}
