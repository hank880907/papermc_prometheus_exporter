package org.rainbowhunter.prometheusexporter.metrics;

public interface MetricGroup {
    void register();
    void unregister();
    void collect();
}
