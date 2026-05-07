package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;
import org.rainbowhunter.prometheusexporter.collector.MultiLabelGauge;
import org.rainbowhunter.prometheusexporter.collector.SimpleGauge;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ServerMetrics extends MetricGroup {

    public ServerMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    public ServerMetrics(FileConfiguration cfg, String prefix) {
        super(cfg, prefix);
    }

    @Override protected String configRoot() { return "server_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(
            simple("mspt_milliseconds",       "Average milliseconds per server tick (last 100 ticks)",          g -> g.set(Bukkit.getAverageTickTime())),
            simple("players_online",           "Number of players currently online",                             g -> g.set(Bukkit.getOnlinePlayers().size())),
            simple("players_max",              "Maximum player slots",                                           g -> g.set(Bukkit.getMaxPlayers())),
            simple("current_tick",             "Current server tick number",                                     g -> g.set(Bukkit.getCurrentTick())),
            simple("worlds",                   "Number of currently loaded worlds",                              g -> g.set(Bukkit.getWorlds().size())),
            multi("tps", "Server TPS", List.of("window"), g -> {
                double[] tps = Bukkit.getTPS();
                g.labelValues("1m").set(tps[0]);
                g.labelValues("5m").set(tps[1]);
                g.labelValues("15m").set(tps[2]);
            }),
            multi("tick_time_milliseconds", "Tick duration quantiles over the last 100 ticks", List.of("quantile"), g -> {
                long[] sorted = sortedTickTimes();
                if (sorted == null) return;
                g.labelValues("max").set(sorted[sorted.length - 1] / 1_000_000.0);
                g.labelValues("p95").set(sorted[(int) (0.95 * (sorted.length - 1))] / 1_000_000.0);
            })
        );
    }

    private MetricCollector simple(String metric, String help, Consumer<Gauge> collect) {
        return new SimpleGauge(metric, prefix + metric, help, collect);
    }

    private MetricCollector multi(String metric, String help, List<String> labels, Consumer<Gauge> collect) {
        return new MultiLabelGauge(metric, prefix + metric, help, labels, collect);
    }

    private static long[] sortedTickTimes() {
        long[] tt = Bukkit.getTickTimes();
        if (tt.length == 0) return null;
        long[] sorted = Arrays.copyOf(tt, tt.length);
        Arrays.sort(sorted);
        return sorted;
    }
}
