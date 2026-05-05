package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.rainbowhunter.prometheusexporter.collector.DelegateCollector;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;

import java.util.List;

public class JvmMetricsGroup extends MetricGroup {

    public JvmMetricsGroup(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot() { return "jvm_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(new DelegateCollector("jvm", () -> JvmMetrics.builder().register()));
    }
}
