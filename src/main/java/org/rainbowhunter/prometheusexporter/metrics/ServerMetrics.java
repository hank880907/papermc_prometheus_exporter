package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ServerMetrics extends MetricGroup<Void> {

    private static final List<Desc<Void>> DESCRIPTIONS = List.of(
        new Desc<>("mspt",         "mc_mspt_milliseconds", "Average milliseconds per server tick (last 100 ticks)", v -> Bukkit.getAverageTickTime()),
        new Desc<>("players",      "mc_players_online",    "Number of players currently online",                    v -> Bukkit.getOnlinePlayers().size()),
        new Desc<>("players",      "mc_players_max",       "Maximum player slots",                                  v -> Bukkit.getMaxPlayers()),
        new Desc<>("current_tick", "mc_current_tick",      "Current server tick number",                            v -> Bukkit.getCurrentTick()),
        new Desc<>("worlds",       "mc_worlds",            "Number of currently loaded worlds",                     v -> Bukkit.getWorlds().size())
    );

    private static final List<GaugeCollector> COLLECTORS = List.of(
        new GaugeCollector("tps", "mc_tps", "Server TPS", List.of("window"), g -> {
            double[] tps = Bukkit.getTPS();
            g.labelValues("1m").set(tps[0]);
            g.labelValues("5m").set(tps[1]);
            g.labelValues("15m").set(tps[2]);
        }),
        new GaugeCollector("tick_time", "mc_tick_time_max_milliseconds",
                "Max tick duration among the last 100 ticks", List.of(), g -> {
            long[] sorted = sortedTickTimes();
            if (sorted != null) g.set(sorted[sorted.length - 1] / 1_000_000.0);
        }),
        new GaugeCollector("tick_time", "mc_tick_time_p95_milliseconds",
                "95th-percentile tick duration among the last 100 ticks", List.of(), g -> {
            long[] sorted = sortedTickTimes();
            if (sorted != null) g.set(sorted[(int) (0.95 * (sorted.length - 1))] / 1_000_000.0);
        })
    );

    public ServerMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot()              { return "server_metrics"; }
    @Override protected Iterable<Void> subjects()        { return Collections.singletonList(null); }
    @Override protected String labelName()               { return null; }
    @Override protected String labelValue(Void subject)  { return null; }
    @Override protected List<Desc<Void>> descriptions()  { return DESCRIPTIONS; }
    @Override protected List<GaugeCollector> collectors(){ return COLLECTORS; }

    private static long[] sortedTickTimes() {
        long[] tt = Bukkit.getTickTimes();
        if (tt.length == 0) return null;
        long[] sorted = Arrays.copyOf(tt, tt.length);
        Arrays.sort(sorted);
        return sorted;
    }
}
