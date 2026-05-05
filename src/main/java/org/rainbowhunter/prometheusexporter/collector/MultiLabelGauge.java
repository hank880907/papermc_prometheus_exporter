package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-label gauge with a free-form collect lambda. Used when label values come from
 * a single source (TPS windows from a {@code double[]}) or when a metric breaks down
 * across multiple dimensions (entities by world and category).
 */
public final class MultiLabelGauge extends MetricCollector {

    private final String configKey;
    private final String name;
    private final String help;
    private final List<String> labels;
    private final Consumer<Gauge> collect;
    private Gauge gauge;

    public MultiLabelGauge(String configKey, String name, String help,
                           List<String> labels, Consumer<Gauge> collect) {
        this.configKey = configKey;
        this.name = name;
        this.help = help;
        this.labels = List.copyOf(labels);
        this.collect = collect;
    }

    @Override public String configKey() { return configKey; }

    @Override protected void doRegister() {
        gauge = Gauge.builder()
                .name(name).help(help)
                .labelNames(labels.toArray(new String[0]))
                .register();
    }

    @Override protected void doUnregister() {
        PrometheusRegistry.defaultRegistry.unregister(gauge);
        gauge = null;
    }

    @Override protected void doCollect() {
        gauge.clear();
        collect.accept(gauge);
    }
}
