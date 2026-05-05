package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;
import org.rainbowhunter.prometheusexporter.collector.MultiLabelGauge;
import org.rainbowhunter.prometheusexporter.collector.SimpleGauge;

import java.util.Arrays;
import java.util.List;

public class ServerMetrics extends MetricGroup {

    public ServerMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot() { return "server_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(
            new SimpleGauge("mspt_milliseconds", "mc_mspt_milliseconds",
                    "Average milliseconds per server tick (last 100 ticks)",
                    g -> g.set(Bukkit.getAverageTickTime())),

            new SimpleGauge("players_online", "mc_players_online",
                    "Number of players currently online",
                    g -> g.set(Bukkit.getOnlinePlayers().size())),

            new SimpleGauge("players_max", "mc_players_max",
                    "Maximum player slots",
                    g -> g.set(Bukkit.getMaxPlayers())),

            new SimpleGauge("current_tick", "mc_current_tick",
                    "Current server tick number",
                    g -> g.set(Bukkit.getCurrentTick())),

            new SimpleGauge("worlds", "mc_worlds",
                    "Number of currently loaded worlds",
                    g -> g.set(Bukkit.getWorlds().size())),

            new MultiLabelGauge("tps", "mc_tps", "Server TPS", List.of("window"), g -> {
                double[] tps = Bukkit.getTPS();
                g.labelValues("1m").set(tps[0]);
                g.labelValues("5m").set(tps[1]);
                g.labelValues("15m").set(tps[2]);
            }),

            new SimpleGauge("tick_time_max_milliseconds", "mc_tick_time_max_milliseconds",
                    "Max tick duration among the last 100 ticks", g -> {
                long[] sorted = sortedTickTimes();
                if (sorted != null) g.set(sorted[sorted.length - 1] / 1_000_000.0);
            }),

            new SimpleGauge("tick_time_p95_milliseconds", "mc_tick_time_p95_milliseconds",
                    "95th-percentile tick duration among the last 100 ticks", g -> {
                long[] sorted = sortedTickTimes();
                if (sorted != null) g.set(sorted[(int) (0.95 * (sorted.length - 1))] / 1_000_000.0);
            })
        );
    }

    private static long[] sortedTickTimes() {
        long[] tt = Bukkit.getTickTimes();
        if (tt.length == 0) return null;
        long[] sorted = Arrays.copyOf(tt, tt.length);
        Arrays.sort(sorted);
        return sorted;
    }
}
