package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.WaterMob;
import org.rainbowhunter.prometheusexporter.collector.LabeledGauge;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;
import org.rainbowhunter.prometheusexporter.collector.MultiLabelGauge;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class WorldMetrics extends MetricGroup {

    public WorldMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot() { return "world_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(
            world("players",              "mc_world_players",         "Number of players per world",                  World::getPlayerCount),
            world("loaded_chunks",        "mc_loaded_chunks",         "Number of loaded chunks per world",            World::getChunkCount),
            world("entities",             "mc_entities",              "Total entity count per world",                 World::getEntityCount),
            world("tile_entities",        "mc_tile_entities",         "Number of tile entities per world",            World::getTileEntityCount),
            world("ticking_tile_entities","mc_ticking_tile_entities", "Number of ticking tile entities per world",    World::getTickableTileEntityCount),
            world("time",                 "mc_world_time",            "In-game clock (0-24000); tracks day/night cycle", World::getTime),
            world("storm",                "mc_world_storm",           "1 if a storm is active, 0 otherwise",          w -> w.hasStorm() ? 1 : 0),
            world("thundering",           "mc_world_thundering",      "1 if a thunderstorm is active, 0 otherwise",   w -> w.isThundering() ? 1 : 0),

            new MultiLabelGauge("entities_by_category", "mc_entities_by_category",
                    "Entity count broken down by category per world",
                    List.of("world", "category"), g -> {
                for (World world : Bukkit.getWorlds()) {
                    int monsters = 0, animals = 0, waterCreatures = 0, ambient = 0, misc = 0;
                    for (Entity entity : world.getEntities()) {
                        if      (entity instanceof Monster)  monsters++;
                        else if (entity instanceof Animals)  animals++;
                        else if (entity instanceof WaterMob) waterCreatures++;
                        else if (entity instanceof Ambient)  ambient++;
                        else                                 misc++;
                    }
                    String name = world.getName();
                    g.labelValues(name, "monsters").set(monsters);
                    g.labelValues(name, "animals").set(animals);
                    g.labelValues(name, "water_creatures").set(waterCreatures);
                    g.labelValues(name, "ambient").set(ambient);
                    g.labelValues(name, "misc").set(misc);
                }
            })
        );
    }

    private static MetricCollector world(String configKey, String name, String help, ToDoubleFunction<World> fn) {
        return new LabeledGauge<>(configKey, name, help, "world",
                Bukkit::getWorlds, World::getName, fn);
    }
}
