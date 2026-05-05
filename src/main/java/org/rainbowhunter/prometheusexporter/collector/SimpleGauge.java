package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.util.function.Consumer;

/**
 * Unlabeled gauge. The lambda decides whether to call {@code set()} (lets metrics
 * skip the update when source data is unavailable, e.g. an empty tick-times array).
 */
public final class SimpleGauge extends MetricCollector {

    private final String configKey;
    private final String name;
    private final String help;
    private final Consumer<Gauge> collect;
    private Gauge gauge;

    public SimpleGauge(String configKey, String name, String help, Consumer<Gauge> collect) {
        this.configKey = configKey;
        this.name = name;
        this.help = help;
        this.collect = collect;
    }

    @Override public String configKey() { return configKey; }

    @Override protected void doRegister() {
        gauge = Gauge.builder().name(name).help(help).register();
    }

    @Override protected void doUnregister() {
        PrometheusRegistry.defaultRegistry.unregister(gauge);
        gauge = null;
    }

    @Override protected void doCollect() {
        collect.accept(gauge);
    }
}
