package org.rainbowhunter.prometheusexporter;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;

public class PrometheusExporterPlugin extends JavaPlugin {

    private HTTPServer httpServer;
    private BukkitTask collectionTask;

    private Gauge tpsGauge;
    private Gauge msptGauge;
    private Gauge playersOnlineGauge;
    private Gauge playersMaxGauge;
    private Gauge currentTickGauge;

    private Gauge worldPlayersGauge;
    private Gauge loadedChunksGauge;
    private Gauge entitiesGauge;
    private Gauge tileEntitiesGauge;
    private Gauge tickingTileEntitiesGauge;

    private Gauge playerPingGauge;
    private Gauge playerHealthGauge;
    private Gauge playerXpLevelGauge;
    private Gauge playerGamemodeGauge;

    private static final GameMode[] GAMEMODES = GameMode.values();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        JvmMetrics.builder().register();

        tpsGauge = Gauge.builder()
                .name("mc_tps")
                .help("Server TPS")
                .labelNames("window")
                .register();

        msptGauge = Gauge.builder()
                .name("mc_mspt_milliseconds")
                .help("Average milliseconds per server tick")
                .register();

        playersOnlineGauge = Gauge.builder()
                .name("mc_players_online")
                .help("Number of players currently online")
                .register();

        playersMaxGauge = Gauge.builder()
                .name("mc_players_max")
                .help("Maximum player slots")
                .register();

        currentTickGauge = Gauge.builder()
                .name("mc_current_tick")
                .help("Current server tick number")
                .register();

        worldPlayersGauge = Gauge.builder()
                .name("mc_world_players")
                .help("Number of players per world")
                .labelNames("world")
                .register();

        loadedChunksGauge = Gauge.builder()
                .name("mc_loaded_chunks")
                .help("Number of loaded chunks per world")
                .labelNames("world")
                .register();

        entitiesGauge = Gauge.builder()
                .name("mc_entities")
                .help("Number of entities per world")
                .labelNames("world")
                .register();

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

        playerPingGauge = Gauge.builder()
                .name("mc_player_ping_milliseconds")
                .help("Player latency in milliseconds")
                .labelNames("player")
                .register();

        playerHealthGauge = Gauge.builder()
                .name("mc_player_health")
                .help("Player health (0.0–20.0 default scale)")
                .labelNames("player")
                .register();

        playerXpLevelGauge = Gauge.builder()
                .name("mc_player_xp_level")
                .help("Player XP level")
                .labelNames("player")
                .register();

        playerGamemodeGauge = Gauge.builder()
                .name("mc_player_gamemode")
                .help("Player active gamemode (1 = active, 0 = inactive)")
                .labelNames("player", "gamemode")
                .register();

        int port = getConfig().getInt("metrics-port", 9940);
        try {
            httpServer = HTTPServer.builder().port(port).buildAndStart();
            getLogger().info("Prometheus metrics available on port " + port);
        } catch (IOException e) {
            getLogger().severe("Failed to start metrics HTTP server: " + e.getMessage());
        }

        long intervalTicks = getConfig().getLong("collection-interval-ticks", 20L);
        collectionTask = getServer().getScheduler().runTaskTimer(this, this::collectMetrics, 0L, intervalTicks);
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

    private void collectMetrics() {
        double[] tps = Bukkit.getTPS();
        tpsGauge.labelValues("1m").set(tps[0]);
        tpsGauge.labelValues("5m").set(tps[1]);
        tpsGauge.labelValues("15m").set(tps[2]);

        msptGauge.set(Bukkit.getAverageTickTime());
        playersOnlineGauge.set(Bukkit.getOnlinePlayers().size());
        playersMaxGauge.set(Bukkit.getMaxPlayers());
        currentTickGauge.set(Bukkit.getCurrentTick());

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            worldPlayersGauge.labelValues(name).set(world.getPlayerCount());
            loadedChunksGauge.labelValues(name).set(world.getChunkCount());
            entitiesGauge.labelValues(name).set(world.getEntityCount());
            tileEntitiesGauge.labelValues(name).set(world.getTileEntityCount());
            tickingTileEntitiesGauge.labelValues(name).set(world.getTickableTileEntityCount());
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            playerPingGauge.labelValues(name).set(player.getPing());
            playerHealthGauge.labelValues(name).set(player.getHealth());
            playerXpLevelGauge.labelValues(name).set(player.getLevel());

            GameMode active = player.getGameMode();
            for (GameMode mode : GAMEMODES) {
                playerGamemodeGauge.labelValues(name, mode.name()).set(mode == active ? 1 : 0);
            }
        }
    }
}
