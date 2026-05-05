package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.configuration.file.FileConfiguration;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * A namespaced bundle of {@link MetricCollector}s sharing a config root
 * (e.g. {@code "player_metrics"}). Each collector is independently gated by
 * {@code <configRoot>.metrics.<configKey>}; the whole group is gated by
 * {@code <configRoot>.enabled}.
 */
public abstract class MetricGroup {

    protected final FileConfiguration cfg;
    private final List<MetricCollector> collectors = new ArrayList<>();
    protected final String prefix;

    protected MetricGroup(FileConfiguration cfg, String prefix) {
        this.cfg = cfg;
        this.prefix = prefix;
    }

    protected MetricGroup(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = "mc_";
    }

    protected abstract String configRoot();

    protected abstract List<MetricCollector> metrics();

    public final void register() {
        if (!cfg.getBoolean(configRoot() + ".enabled", true)) return;
        for (MetricCollector m : metrics()) {
            m.register(cfg, configRoot());
            collectors.add(m);
        }
    }

    public final void unregister() {
        collectors.forEach(MetricCollector::unregister);
        collectors.clear();
    }

    public final void collect() {
        collectors.forEach(MetricCollector::collect);
    }
}
