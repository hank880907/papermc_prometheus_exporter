package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Per-subject gauge with a single label (e.g. one value per online player). Each
 * collection clears the gauge to drop stale label combinations (logged-off players,
 * unloaded worlds) before re-emitting current subjects.
 */
public final class LabeledGauge<T> extends MetricCollector {

    private final String configKey;
    private final String name;
    private final String help;
    private final String labelName;
    private final Supplier<? extends Iterable<? extends T>> subjects;
    private final Function<T, String> labelFn;
    private final ToDoubleFunction<T> valueFn;
    private Gauge gauge;

    public LabeledGauge(String configKey, String name, String help, String labelName,
                        Supplier<? extends Iterable<? extends T>> subjects,
                        Function<T, String> labelFn,
                        ToDoubleFunction<T> valueFn) {
        this.configKey = configKey;
        this.name = name;
        this.help = help;
        this.labelName = labelName;
        this.subjects = subjects;
        this.labelFn = labelFn;
        this.valueFn = valueFn;
    }

    @Override public String configKey() { return configKey; }

    @Override protected void doRegister() {
        gauge = Gauge.builder().name(name).help(help).labelNames(labelName).register();
    }

    @Override protected void doUnregister() {
        PrometheusRegistry.defaultRegistry.unregister(gauge);
        gauge = null;
    }

    @Override protected void doCollect() {
        gauge.clear();
        for (T s : subjects.get()) {
            gauge.labelValues(labelFn.apply(s)).set(valueFn.applyAsDouble(s));
        }
    }
}
