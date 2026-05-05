package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;

public class ServerMetrics implements MetricGroup {

    private final boolean enabled;
    private final boolean cfgTps, cfgMspt, cfgTickTime, cfgPlayers, cfgCurrentTick, cfgWorlds;

    private Gauge tpsGauge;
    private Gauge msptGauge;
    private Gauge tickTimeMaxGauge;
    private Gauge tickTimeP95Gauge;
    private Gauge playersOnlineGauge;
    private Gauge playersMaxGauge;
    private Gauge currentTickGauge;
    private Gauge worldsGauge;

    public ServerMetrics(FileConfiguration cfg) {
        enabled         = cfg.getBoolean("server_metrics.enabled",      true);
        cfgTps          = cfg.getBoolean("server_metrics.tps",          true);
        cfgMspt         = cfg.getBoolean("server_metrics.mspt",         true);
        cfgTickTime     = cfg.getBoolean("server_metrics.tick_time",     true);
        cfgPlayers      = cfg.getBoolean("server_metrics.players",       true);
        cfgCurrentTick  = cfg.getBoolean("server_metrics.current_tick",  true);
        cfgWorlds       = cfg.getBoolean("server_metrics.worlds",        true);
    }

    @Override
    public void register() {
        if (!enabled) return;

        if (cfgTps) {
            tpsGauge = Gauge.builder()
                    .name("mc_tps")
                    .help("Server TPS")
                    .labelNames("window")
                    .register();
        }
        if (cfgMspt) {
            msptGauge = Gauge.builder()
                    .name("mc_mspt_milliseconds")
                    .help("Average milliseconds per server tick (last 100 ticks)")
                    .register();
        }
        if (cfgTickTime) {
            tickTimeMaxGauge = Gauge.builder()
                    .name("mc_tick_time_max_milliseconds")
                    .help("Max tick duration among the last 100 ticks")
                    .register();
            tickTimeP95Gauge = Gauge.builder()
                    .name("mc_tick_time_p95_milliseconds")
                    .help("95th-percentile tick duration among the last 100 ticks")
                    .register();
        }
        if (cfgPlayers) {
            playersOnlineGauge = Gauge.builder()
                    .name("mc_players_online")
                    .help("Number of players currently online")
                    .register();
            playersMaxGauge = Gauge.builder()
                    .name("mc_players_max")
                    .help("Maximum player slots")
                    .register();
        }
        if (cfgCurrentTick) {
            currentTickGauge = Gauge.builder()
                    .name("mc_current_tick")
                    .help("Current server tick number")
                    .register();
        }
        if (cfgWorlds) {
            worldsGauge = Gauge.builder()
                    .name("mc_worlds")
                    .help("Number of currently loaded worlds")
                    .register();
        }
    }

    @Override
    public void unregister() {
        tpsGauge          = clear(tpsGauge);
        msptGauge         = clear(msptGauge);
        tickTimeMaxGauge  = clear(tickTimeMaxGauge);
        tickTimeP95Gauge  = clear(tickTimeP95Gauge);
        playersOnlineGauge = clear(playersOnlineGauge);
        playersMaxGauge   = clear(playersMaxGauge);
        currentTickGauge  = clear(currentTickGauge);
        worldsGauge       = clear(worldsGauge);
    }

    @Override
    public void collect() {
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
            if (tickTimes.length > 0) {
                long[] sorted = Arrays.copyOf(tickTimes, tickTimes.length);
                Arrays.sort(sorted);
                if (tickTimeMaxGauge != null) tickTimeMaxGauge.set(sorted[sorted.length - 1] / 1_000_000.0);
                if (tickTimeP95Gauge != null) tickTimeP95Gauge.set(sorted[(int) (0.95 * (sorted.length - 1))] / 1_000_000.0);
            }
        }
        if (playersOnlineGauge != null) playersOnlineGauge.set(Bukkit.getOnlinePlayers().size());
        if (playersMaxGauge    != null) playersMaxGauge.set(Bukkit.getMaxPlayers());
        if (currentTickGauge   != null) currentTickGauge.set(Bukkit.getCurrentTick());
        if (worldsGauge        != null) worldsGauge.set(Bukkit.getWorlds().size());
    }

    private static Gauge clear(Gauge g) {
        if (g != null) PrometheusRegistry.defaultRegistry.unregister(g);
        return null;
    }
}
