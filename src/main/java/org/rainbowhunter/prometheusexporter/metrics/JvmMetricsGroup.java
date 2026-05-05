package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.bukkit.configuration.file.FileConfiguration;

public class JvmMetricsGroup implements MetricGroup {

    private final boolean enabled;

    public JvmMetricsGroup(FileConfiguration cfg) {
        enabled = cfg.getBoolean("jvm_metrics.enabled", true);
    }

    @Override
    public void register() {
        if (!enabled) return;
        JvmMetrics.builder().register();
    }

    @Override
    public void unregister() {
        // JvmMetrics registers collectors on the default registry with no clean unregister path;
        // reload() leaves them in place (matches the caveat documented in PECommands.java).
    }

    @Override
    public void collect() {
        // JvmMetrics collectors push data themselves; nothing to do here.
    }
}
