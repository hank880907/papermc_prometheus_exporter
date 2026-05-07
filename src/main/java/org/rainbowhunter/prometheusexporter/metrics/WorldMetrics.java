package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.rainbowhunter.prometheusexporter.collector.LabeledGauge;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;
import org.rainbowhunter.prometheusexporter.collector.MultiLabelGauge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public class WorldMetrics extends MetricGroup {

    public WorldMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    public WorldMetrics(FileConfiguration cfg, String prefix) {
        super(cfg, prefix);
    }

    @Override protected String configRoot() { return "world_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(
            world("players",               "Number of players per world",                     World::getPlayerCount),
            world("loaded_chunks",         "Number of loaded chunks per world",               World::getChunkCount),
            world("entities",              "Total entity count per world",                    World::getEntityCount),
            world("tile_entities",         "Number of tile entities per world",               World::getTileEntityCount),
            world("ticking_tile_entities", "Number of ticking tile entities per world",       World::getTickableTileEntityCount),
            world("time",                  "In-game clock (0-24000); tracks day/night cycle", World::getTime),
            world("storm",                 "1 if a storm is active, 0 otherwise",             w -> w.hasStorm() ? 1 : 0),
            world("thundering",            "1 if a thunderstorm is active, 0 otherwise",      w -> w.isThundering() ? 1 : 0),

            new MultiLabelGauge("entities_by_type", prefix + "world_entities_by_type",
                    "Entity count broken down by type per world",
                    List.of("world", "type"), g -> {
                for (World world : Bukkit.getWorlds()) {
                    Map<String, Integer> counts = new HashMap<>();
                    for (Entity entity : world.getEntities()) {
                        counts.merge(entity.getType().getKey().getKey(), 1, Integer::sum);
                    }
                    String name = world.getName();
                    counts.forEach((type, count) -> g.labelValues(name, type).set(count));
                }
            })
        );
    }

    private MetricCollector world(String metric, String help, ToDoubleFunction<World> fn) {
        return new LabeledGauge<>(metric, prefix + "world_" + metric, help, "world",
                Bukkit::getWorlds, World::getName, fn);
    }
}
