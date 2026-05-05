package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.WaterMob;

import java.util.HashMap;
import java.util.Map;

public class WorldMetrics implements MetricGroup {

    private final boolean enabled;
    private final boolean cfgPlayers, cfgChunks, cfgEntities, cfgEntitiesByCategory,
                          cfgTileEntities, cfgWorldTime, cfgWeather;

    private Gauge worldPlayersGauge;
    private Gauge loadedChunksGauge;
    private Gauge entitiesGauge;
    private Gauge entitiesByCategoryGauge;
    private Gauge tileEntitiesGauge;
    private Gauge tickingTileEntitiesGauge;
    private Gauge worldTimeGauge;
    private Gauge worldStormGauge;
    private Gauge worldThunderingGauge;

    public WorldMetrics(FileConfiguration cfg) {
        enabled                = cfg.getBoolean("world_metrics.enabled",              true);
        cfgPlayers             = cfg.getBoolean("world_metrics.players",              true);
        cfgChunks              = cfg.getBoolean("world_metrics.chunks",               true);
        cfgEntities            = cfg.getBoolean("world_metrics.entities",             true);
        cfgEntitiesByCategory  = cfg.getBoolean("world_metrics.entities_by_category", true);
        cfgTileEntities        = cfg.getBoolean("world_metrics.tile_entities",        true);
        cfgWorldTime           = cfg.getBoolean("world_metrics.world_time",           true);
        cfgWeather             = cfg.getBoolean("world_metrics.weather",              true);
    }

    @Override
    public void register() {
        if (!enabled) return;

        if (cfgPlayers) {
            worldPlayersGauge = Gauge.builder()
                    .name("mc_world_players")
                    .help("Number of players per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgChunks) {
            loadedChunksGauge = Gauge.builder()
                    .name("mc_loaded_chunks")
                    .help("Number of loaded chunks per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgEntities) {
            entitiesGauge = Gauge.builder()
                    .name("mc_entities")
                    .help("Total entity count per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgEntitiesByCategory) {
            entitiesByCategoryGauge = Gauge.builder()
                    .name("mc_entities_by_category")
                    .help("Entity count broken down by category per world")
                    .labelNames("world", "category")
                    .register();
        }
        if (cfgTileEntities) {
            tileEntitiesGauge = Gauge.builder()
                    .name("mc_tile_entities")
                    .help("Number of tile entities per world")
                    .labelNames("world")
                    .register();
            tickingTileEntitiesGauge = Gauge.builder()
                    .name("mc_ticking_tile_entities")
                    .help("Number of ticking tile entities per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgWorldTime) {
            worldTimeGauge = Gauge.builder()
                    .name("mc_world_time")
                    .help("In-game clock (0-24000); tracks day/night cycle")
                    .labelNames("world")
                    .register();
        }
        if (cfgWeather) {
            worldStormGauge = Gauge.builder()
                    .name("mc_world_storm")
                    .help("1 if a storm is active, 0 otherwise")
                    .labelNames("world")
                    .register();
            worldThunderingGauge = Gauge.builder()
                    .name("mc_world_thundering")
                    .help("1 if a thunderstorm is active, 0 otherwise")
                    .labelNames("world")
                    .register();
        }
    }

    @Override
    public void unregister() {
        worldPlayersGauge        = clear(worldPlayersGauge);
        loadedChunksGauge        = clear(loadedChunksGauge);
        entitiesGauge            = clear(entitiesGauge);
        entitiesByCategoryGauge  = clear(entitiesByCategoryGauge);
        tileEntitiesGauge        = clear(tileEntitiesGauge);
        tickingTileEntitiesGauge = clear(tickingTileEntitiesGauge);
        worldTimeGauge           = clear(worldTimeGauge);
        worldStormGauge          = clear(worldStormGauge);
        worldThunderingGauge     = clear(worldThunderingGauge);
    }

    @Override
    public void collect() {
        if (worldPlayersGauge == null && loadedChunksGauge == null && entitiesGauge == null
                && entitiesByCategoryGauge == null && tileEntitiesGauge == null
                && tickingTileEntitiesGauge == null && worldTimeGauge == null
                && worldStormGauge == null && worldThunderingGauge == null) return;

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (worldPlayersGauge        != null) worldPlayersGauge.labelValues(name).set(world.getPlayerCount());
            if (loadedChunksGauge        != null) loadedChunksGauge.labelValues(name).set(world.getChunkCount());
            if (entitiesGauge            != null) entitiesGauge.labelValues(name).set(world.getEntityCount());
            if (tileEntitiesGauge        != null) tileEntitiesGauge.labelValues(name).set(world.getTileEntityCount());
            if (tickingTileEntitiesGauge != null) tickingTileEntitiesGauge.labelValues(name).set(world.getTickableTileEntityCount());
            if (worldTimeGauge           != null) worldTimeGauge.labelValues(name).set(world.getTime());
            if (worldStormGauge          != null) worldStormGauge.labelValues(name).set(world.hasStorm() ? 1 : 0);
            if (worldThunderingGauge     != null) worldThunderingGauge.labelValues(name).set(world.isThundering() ? 1 : 0);

            if (entitiesByCategoryGauge != null) {
                Map<String, Integer> cats = new HashMap<>();
                cats.put("monsters", 0);
                cats.put("animals", 0);
                cats.put("water_creatures", 0);
                cats.put("ambient", 0);
                cats.put("misc", 0);
                for (Entity entity : world.getEntities()) {
                    String cat;
                    if      (entity instanceof Monster)  cat = "monsters";
                    else if (entity instanceof Animals)  cat = "animals";
                    else if (entity instanceof WaterMob) cat = "water_creatures";
                    else if (entity instanceof Ambient)  cat = "ambient";
                    else                                 cat = "misc";
                    cats.merge(cat, 1, Integer::sum);
                }
                for (Map.Entry<String, Integer> entry : cats.entrySet()) {
                    entitiesByCategoryGauge.labelValues(name, entry.getKey()).set(entry.getValue());
                }
            }
        }
    }

    private static Gauge clear(Gauge g) {
        if (g != null) PrometheusRegistry.defaultRegistry.unregister(g);
        return null;
    }
}
