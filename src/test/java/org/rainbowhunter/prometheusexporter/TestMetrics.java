package org.rainbowhunter.prometheusexporter;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;

import java.util.List;

public final class TestMetrics {

    private TestMetrics() {}

    public static GaugeSnapshot findGauge(String name) {
        for (MetricSnapshot s : PrometheusRegistry.defaultRegistry.scrape()) {
            if (s.getMetadata().getName().equals(name)) return (GaugeSnapshot) s;
        }
        throw new AssertionError("gauge not found: " + name);
    }

    public static List<String> registeredMetricNames() {
        return PrometheusRegistry.defaultRegistry.scrape().stream()
                .map(MetricSnapshot::getMetadata)
                .map(MetricMetadata::getName)
                .toList();
    }
}
