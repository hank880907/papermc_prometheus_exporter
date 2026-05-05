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
    private final List<MetricCollector> active = new ArrayList<>();

    protected MetricGroup(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    protected abstract String configRoot();

    protected abstract List<MetricCollector> metrics();

    public final void register() {
        if (!cfg.getBoolean(configRoot() + ".enabled", true)) return;
        for (MetricCollector m : metrics()) {
            m.register(cfg, configRoot());
            active.add(m);
        }
    }

    public final void unregister() {
        active.forEach(MetricCollector::unregister);
        active.clear();
    }

    public final void collect() {
        active.forEach(MetricCollector::collect);
    }
}
