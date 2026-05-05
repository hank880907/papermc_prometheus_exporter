package org.rainbowhunter.prometheusexporter;

import io.prometheus.metrics.core.metrics.Gauge;
import org.rainbowhunter.prometheusexporter.commands.PECommands;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PrometheusExporterPlugin extends JavaPlugin {

    private HTTPServer httpServer;
    private BukkitTask collectionTask;

    // Server
    private Gauge tpsGauge;
    private Gauge msptGauge;
    private Gauge tickTimeMaxGauge;
    private Gauge tickTimeP95Gauge;
    private Gauge playersOnlineGauge;
    private Gauge playersMaxGauge;
    private Gauge currentTickGauge;
    private Gauge worldsGauge;

    // Per-world
    private Gauge worldPlayersGauge;
    private Gauge loadedChunksGauge;
    private Gauge entitiesGauge;
    private Gauge entitiesByCategoryGauge;
    private Gauge tileEntitiesGauge;
    private Gauge tickingTileEntitiesGauge;
    private Gauge worldTimeGauge;
    private Gauge worldStormGauge;
    private Gauge worldThunderingGauge;

    // Per-player
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

    private static final GameMode[] GAMEMODES = GameMode.values();

    // Config: group-level enable flags
    private boolean cfgServerEnabled, cfgWorldEnabled, cfgPlayerEnabled, cfgJvmEnabled;
    // Config: server_metrics sub-flags
    private boolean cfgServerTps, cfgServerMspt, cfgServerTickTime, cfgServerPlayers,
                    cfgServerCurrentTick, cfgServerWorlds;
    // Config: world_metrics sub-flags
    private boolean cfgWorldPlayers, cfgWorldChunks, cfgWorldEntities,
                    cfgWorldEntitiesByCategory, cfgWorldTileEntities,
                    cfgWorldWorldTime, cfgWorldWeather;
    // Config: player_metrics sub-flags
    private boolean cfgPlayerPing, cfgPlayerHealth, cfgPlayerFood, cfgPlayerSaturation,
                    cfgPlayerXp, cfgPlayerFlying, cfgPlayerGamemode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigFlags();

        if (cfgJvmEnabled) {
            JvmMetrics.builder().register();
        }

        registerGauges();

        int port = getConfig().getInt("metrics-port", 9940);
        try {
            httpServer = HTTPServer.builder().port(port).buildAndStart();
            getLogger().info("Prometheus metrics available on port " + port);
        } catch (IOException e) {
            getLogger().severe("Failed to start metrics HTTP server: " + e.getMessage());
        }

        long intervalTicks = getConfig().getLong("collection-interval-ticks", 20L);
        collectionTask = getServer().getScheduler().runTaskTimer(this, this::collectMetrics, 0L, intervalTicks);

        new PECommands(this, this::reload).register();
    }

    @Override
    public void onDisable() {
        if (collectionTask != null) {
            collectionTask.cancel();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    private void reload() {
        if (collectionTask != null) {
            collectionTask.cancel();
            collectionTask = null;
        }
        unregisterGauges();
        reloadConfig();
        loadConfigFlags();
        registerGauges();
        long intervalTicks = getConfig().getLong("collection-interval-ticks", 20L);
        collectionTask = getServer().getScheduler().runTaskTimer(this, this::collectMetrics, 0L, intervalTicks);
        getLogger().info("Configuration reloaded");
    }

    private Gauge clear(Gauge g) {
        if (g != null) PrometheusRegistry.defaultRegistry.unregister(g);
        return null;
    }

    private void unregisterGauges() {
        tpsGauge                   = clear(tpsGauge);
        msptGauge                  = clear(msptGauge);
        tickTimeMaxGauge           = clear(tickTimeMaxGauge);
        tickTimeP95Gauge           = clear(tickTimeP95Gauge);
        playersOnlineGauge         = clear(playersOnlineGauge);
        playersMaxGauge            = clear(playersMaxGauge);
        currentTickGauge           = clear(currentTickGauge);
        worldsGauge                = clear(worldsGauge);

        worldPlayersGauge          = clear(worldPlayersGauge);
        loadedChunksGauge          = clear(loadedChunksGauge);
        entitiesGauge              = clear(entitiesGauge);
        entitiesByCategoryGauge    = clear(entitiesByCategoryGauge);
        tileEntitiesGauge          = clear(tileEntitiesGauge);
        tickingTileEntitiesGauge   = clear(tickingTileEntitiesGauge);
        worldTimeGauge             = clear(worldTimeGauge);
        worldStormGauge            = clear(worldStormGauge);
        worldThunderingGauge       = clear(worldThunderingGauge);

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

    private void registerGauges() {
        if (cfgServerEnabled && cfgServerTps) {
            tpsGauge = Gauge.builder()
                    .name("mc_tps")
                    .help("Server TPS")
                    .labelNames("window")
                    .register();
        }
        if (cfgServerEnabled && cfgServerMspt) {
            msptGauge = Gauge.builder()
                    .name("mc_mspt_milliseconds")
                    .help("Average milliseconds per server tick (last 100 ticks)")
                    .register();
        }
        if (cfgServerEnabled && cfgServerTickTime) {
            tickTimeMaxGauge = Gauge.builder()
                    .name("mc_tick_time_max_milliseconds")
                    .help("Max tick duration among the last 100 ticks")
                    .register();
            tickTimeP95Gauge = Gauge.builder()
                    .name("mc_tick_time_p95_milliseconds")
                    .help("95th-percentile tick duration among the last 100 ticks")
                    .register();
        }
        if (cfgServerEnabled && cfgServerPlayers) {
            playersOnlineGauge = Gauge.builder()
                    .name("mc_players_online")
                    .help("Number of players currently online")
                    .register();
            playersMaxGauge = Gauge.builder()
                    .name("mc_players_max")
                    .help("Maximum player slots")
                    .register();
        }
        if (cfgServerEnabled && cfgServerCurrentTick) {
            currentTickGauge = Gauge.builder()
                    .name("mc_current_tick")
                    .help("Current server tick number")
                    .register();
        }
        if (cfgServerEnabled && cfgServerWorlds) {
            worldsGauge = Gauge.builder()
                    .name("mc_worlds")
                    .help("Number of currently loaded worlds")
                    .register();
        }

        if (cfgWorldEnabled && cfgWorldPlayers) {
            worldPlayersGauge = Gauge.builder()
                    .name("mc_world_players")
                    .help("Number of players per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgWorldEnabled && cfgWorldChunks) {
            loadedChunksGauge = Gauge.builder()
                    .name("mc_loaded_chunks")
                    .help("Number of loaded chunks per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgWorldEnabled && cfgWorldEntities) {
            entitiesGauge = Gauge.builder()
                    .name("mc_entities")
                    .help("Total entity count per world")
                    .labelNames("world")
                    .register();
        }
        if (cfgWorldEnabled && cfgWorldEntitiesByCategory) {
            entitiesByCategoryGauge = Gauge.builder()
                    .name("mc_entities_by_category")
                    .help("Entity count broken down by category per world")
                    .labelNames("world", "category")
                    .register();
        }
        if (cfgWorldEnabled && cfgWorldTileEntities) {
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
        if (cfgWorldEnabled && cfgWorldWorldTime) {
            worldTimeGauge = Gauge.builder()
                    .name("mc_world_time")
                    .help("In-game clock (0-24000); tracks day/night cycle")
                    .labelNames("world")
                    .register();
        }
        if (cfgWorldEnabled && cfgWorldWeather) {
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

        if (cfgPlayerEnabled && cfgPlayerPing) {
            playerPingGauge = Gauge.builder()
                    .name("mc_player_ping_milliseconds")
                    .help("Player latency in milliseconds")
                    .labelNames("player")
                    .register();
        }
        if (cfgPlayerEnabled && cfgPlayerHealth) {
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
        if (cfgPlayerEnabled && cfgPlayerFood) {
            playerFoodLevelGauge = Gauge.builder()
                    .name("mc_player_food_level")
                    .help("Hunger level per player (0-20)")
                    .labelNames("player")
                    .register();
        }
        if (cfgPlayerEnabled && cfgPlayerSaturation) {
            playerSaturationGauge = Gauge.builder()
                    .name("mc_player_saturation")
                    .help("Saturation per player (0.0-20.0+)")
                    .labelNames("player")
                    .register();
        }
        if (cfgPlayerEnabled && cfgPlayerXp) {
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
        if (cfgPlayerEnabled && cfgPlayerFlying) {
            playerFlyingGauge = Gauge.builder()
                    .name("mc_player_flying")
                    .help("1 if the player is flying, 0 otherwise")
                    .labelNames("player")
                    .register();
        }
        if (cfgPlayerEnabled && cfgPlayerGamemode) {
            playerGamemodeGauge = Gauge.builder()
                    .name("mc_player_gamemode")
                    .help("Player active gamemode (1 = active, 0 = inactive)")
                    .labelNames("player", "gamemode")
                    .register();
        }
    }

    private void loadConfigFlags() {
        cfgServerEnabled          = getConfig().getBoolean("server_metrics.enabled",             true);
        cfgServerTps              = getConfig().getBoolean("server_metrics.tps",                 true);
        cfgServerMspt             = getConfig().getBoolean("server_metrics.mspt",                true);
        cfgServerTickTime         = getConfig().getBoolean("server_metrics.tick_time",           true);
        cfgServerPlayers          = getConfig().getBoolean("server_metrics.players",             true);
        cfgServerCurrentTick      = getConfig().getBoolean("server_metrics.current_tick",        true);
        cfgServerWorlds           = getConfig().getBoolean("server_metrics.worlds",              true);

        cfgWorldEnabled           = getConfig().getBoolean("world_metrics.enabled",              true);
        cfgWorldPlayers           = getConfig().getBoolean("world_metrics.players",              true);
        cfgWorldChunks            = getConfig().getBoolean("world_metrics.chunks",               true);
        cfgWorldEntities          = getConfig().getBoolean("world_metrics.entities",             true);
        cfgWorldEntitiesByCategory= getConfig().getBoolean("world_metrics.entities_by_category", true);
        cfgWorldTileEntities      = getConfig().getBoolean("world_metrics.tile_entities",        true);
        cfgWorldWorldTime         = getConfig().getBoolean("world_metrics.world_time",           true);
        cfgWorldWeather           = getConfig().getBoolean("world_metrics.weather",              true);

        cfgPlayerEnabled          = getConfig().getBoolean("player_metrics.enabled",             true);
        cfgPlayerPing             = getConfig().getBoolean("player_metrics.ping",                true);
        cfgPlayerHealth           = getConfig().getBoolean("player_metrics.health",              true);
        cfgPlayerFood             = getConfig().getBoolean("player_metrics.food",                true);
        cfgPlayerSaturation       = getConfig().getBoolean("player_metrics.saturation",          true);
        cfgPlayerXp               = getConfig().getBoolean("player_metrics.xp",                 true);
        cfgPlayerFlying           = getConfig().getBoolean("player_metrics.flying",              true);
        cfgPlayerGamemode         = getConfig().getBoolean("player_metrics.gamemode",            true);

        cfgJvmEnabled             = getConfig().getBoolean("jvm_metrics.enabled",                true);
    }

    private void collectMetrics() {
        collectServerMetrics();
        collectWorldMetrics();
        collectPlayerMetrics();
    }

    private void collectServerMetrics() {
        if (!cfgServerEnabled) return;

        if (tpsGauge != null) {
            double[] tps = Bukkit.getTPS();
            tpsGauge.labelValues("1m").set(tps[0]);
            tpsGauge.labelValues("5m").set(tps[1]);
            tpsGauge.labelValues("15m").set(tps[2]);
        }
        if (msptGauge != null) {
            msptGauge.set(Bukkit.getAverageTickTime());
        }
        if (tickTimeMaxGauge != null || tickTimeP95Gauge != null) {
            long[] tickTimes = Bukkit.getTickTimes();
            long[] sorted = Arrays.copyOf(tickTimes, tickTimes.length);
            Arrays.sort(sorted);
            if (tickTimeMaxGauge != null) tickTimeMaxGauge.set(sorted[sorted.length - 1] / 1_000_000.0);
            if (tickTimeP95Gauge != null) tickTimeP95Gauge.set(sorted[(int) (0.95 * (sorted.length - 1))] / 1_000_000.0);
        }
        if (playersOnlineGauge != null) playersOnlineGauge.set(Bukkit.getOnlinePlayers().size());
        if (playersMaxGauge    != null) playersMaxGauge.set(Bukkit.getMaxPlayers());
        if (currentTickGauge   != null) currentTickGauge.set(Bukkit.getCurrentTick());
        if (worldsGauge        != null) worldsGauge.set(Bukkit.getWorlds().size());
    }

    private void collectWorldMetrics() {
        if (!cfgWorldEnabled) return;
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

    private void collectPlayerMetrics() {
        if (!cfgPlayerEnabled) return;
        if (playerPingGauge == null && playerHealthGauge == null && playerMaxHealthGauge == null
                && playerFoodLevelGauge == null && playerSaturationGauge == null
                && playerXpLevelGauge == null && playerXpProgressGauge == null
                && playerTotalExperienceGauge == null && playerFlyingGauge == null
                && playerGamemodeGauge == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (playerPingGauge            != null) playerPingGauge.labelValues(name).set(player.getPing());
            if (playerHealthGauge          != null) playerHealthGauge.labelValues(name).set(player.getHealth());
            if (playerMaxHealthGauge       != null) playerMaxHealthGauge.labelValues(name).set(player.getMaxHealth());
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
}
