package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.configuration.file.FileConfiguration;
import org.rainbowhunter.prometheusexporter.collector.DelegateCollector;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;

import java.util.List;

public class JvmMetrics extends MetricGroup {

    public JvmMetrics(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot() { return "jvm_metrics"; }

    @Override protected List<MetricCollector> metrics() {
        return List.of(new DelegateCollector("jvm", () -> io.prometheus.metrics.instrumentation.jvm.JvmMetrics.builder().register()));
    }
}
