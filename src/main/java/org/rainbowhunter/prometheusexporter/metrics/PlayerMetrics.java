package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class PlayerMetrics extends MetricGroup<Player> {

    private static final List<Desc<Player>> DESCRIPTIONS = List.of(
        new Desc<>("ping",       "mc_player_ping_milliseconds", "Player latency in milliseconds",                          Player::getPing),
        new Desc<>("health",     "mc_player_health",            "Player health",                                           Player::getHealth),
        new Desc<>("health",     "mc_player_max_health",        "Player max health (useful when custom attributes are applied)", p -> {
            var attr = p.getAttribute(Attribute.MAX_HEALTH);
            return attr != null ? attr.getValue() : 0.0;
        }),
        new Desc<>("food",       "mc_player_food_level",        "Hunger level per player (0-20)",                          Player::getFoodLevel),
        new Desc<>("saturation", "mc_player_saturation",        "Saturation per player (0.0-20.0+)",                       Player::getSaturation),
        new Desc<>("xp",         "mc_player_xp_level",          "Player XP level",                                         Player::getLevel),
        new Desc<>("xp",         "mc_player_xp_progress",       "Fractional XP progress within current level (0.0-1.0)",   Player::getExp),
        new Desc<>("xp",         "mc_player_total_experience",  "Total accumulated XP per player",                         Player::getTotalExperience),
        new Desc<>("flying",     "mc_player_flying",            "1 if the player is flying, 0 otherwise",                  p -> p.isFlying() ? 1.0 : 0.0),
        new Desc<>("gamemode",   "mc_player_gamemode",          "Player active gamemode (0=survival, 1=creative, 2=adventure, 3=spectator)", p -> p.getGameMode().getValue())
    );

    public PlayerMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot()                   { return "player_metrics"; }
    @Override protected Iterable<? extends Player> subjects() { return Bukkit.getOnlinePlayers(); }
    @Override protected String labelName()                    { return "player"; }
    @Override protected String labelValue(Player p)           { return p.getName(); }
    @Override protected List<Desc<Player>> descriptions()     { return DESCRIPTIONS; }
}
