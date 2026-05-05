package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Declarative base for a related set of Prometheus gauges (server, world, player, ...).
 * <p>
 * Subclasses describe their metrics in two forms instead of writing register/collect code:
 * <ul>
 *   <li>{@link #descriptions()} — the common shape: one value per subject of type {@code T},
 *       with an optional single label ({@link #labelName()} / {@link #labelValue}). The base
 *       class iterates {@link #subjects()} each tick and sets every gauge per subject.</li>
 *   <li>{@link #collectors()} — irregular metrics that don't fit the description shape:
 *       multi-label gauges, fixed label values from a single source (e.g. a
 *       {@code double[]}), or several gauges sharing one expensive computation. Each
 *       collector owns a {@link Consumer Consumer&lt;Gauge&gt;} lambda that fills the gauge
 *       however it likes.</li>
 * </ul>
 * Both forms are gated by config flags: a metric registers only if both
 * {@code <configRoot>.enabled} and {@code <configRoot>.metrics.<cfgKey>} are true (default
 * true if absent). Multiple metrics can share a {@code cfgKey} to be toggled together.
 * <p>
 * Lifecycle (driven by the plugin):
 * <pre>
 *   register()    // gate flags, build gauges, call extraRegister()
 *   collect() x N // clear labeled gauges, evaluate descriptions, run collector lambdas, extraCollect()
 *   unregister()  // drop gauges from the default registry, call extraUnregister()
 * </pre>
 * The {@code extra*} hooks are an escape hatch for groups that don't fit either declarative
 * form — currently only {@link JvmMetricsGroup}, which delegates to
 * {@code JvmMetrics.builder().register()} and has no per-tick work.
 *
 * @param <T> iteration subject (e.g. {@link org.bukkit.entity.Player},
 *            {@link org.bukkit.World}). Use {@link Void} for groups with no per-subject
 *            iteration; {@link #subjects()} should then yield a single {@code null}.
 */
public abstract class MetricGroup<T> {

    /**
     * One value per subject, with at most one label.
     *
     * @param cfgKey suffix appended to {@code <configRoot>.metrics.} to gate registration
     * @param name   Prometheus metric name (e.g. {@code mc_player_health})
     * @param help   HELP text exposed on the {@code /metrics} endpoint
     * @param fn     extracts the gauge value from the subject; receives {@code null} when
     *               {@code T} is {@link Void}
     */
    protected record Desc<T>(String cfgKey, String name, String help, ToDoubleFunction<T> fn) {}

    /**
     * Irregular gauge with a free-form collect lambda.
     * <p>
     * Use this when a metric has more than one label, fixed label values from a single
     * source, or shares a computation with sibling metrics. The base class registers/clears
     * the gauge and invokes {@code collect}; the lambda is responsible for setting every
     * time series the gauge should expose this tick.
     * <p>
     * The base class clears the gauge before each invocation when {@code labels} is
     * non-empty (to drop stale label combinations). Unlabeled gauges are not cleared —
     * the lambda's {@code .set()} replaces the single value in place.
     *
     * @param cfgKey  config flag suffix (same convention as {@link Desc#cfgKey()})
     * @param name    Prometheus metric name
     * @param help    HELP text
     * @param labels  label names; pass {@link List#of()} for an unlabeled gauge
     * @param collect lambda invoked each {@link MetricGroup#collect()} with the registered gauge
     */
    protected record GaugeCollector(String cfgKey, String name, String help,
                                    List<String> labels, Consumer<Gauge> collect) {
        public GaugeCollector {
            labels = List.copyOf(labels);
        }
    }

    private record RegisteredDesc<T>(Desc<T> desc, Gauge gauge) {}
    private record RegisteredCollector(GaugeCollector collector, Gauge gauge) {}

    protected final FileConfiguration cfg;
    private final List<RegisteredDesc<T>> registeredDescs = new ArrayList<>();
    private final List<RegisteredCollector> registeredCollectors = new ArrayList<>();
    private boolean enabled;

    protected MetricGroup(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    /** Config namespace for this group, e.g. {@code "server_metrics"}. */
    protected abstract String configRoot();

    /**
     * Subjects to iterate each {@link #collect()}. For non-iterating groups (e.g. server-wide
     * gauges) return a single-element iterable containing {@code null}.
     */
    protected abstract Iterable<? extends T> subjects();

    /** Label name for {@link #descriptions()}, or {@code null} for unlabeled descriptions. */
    protected abstract String labelName();

    /** Label value for the given subject; not called when {@link #labelName()} is {@code null}. */
    protected abstract String labelValue(T subject);

    /** Declarative metrics for this group. Static {@code List.of(...)} is fine and idiomatic. */
    protected abstract List<Desc<T>> descriptions();

    /** Irregular metrics for this group. Override only if the group has any. */
    protected List<GaugeCollector> collectors() { return List.of(); }

    /** Escape hatches for groups that don't fit the declarative model (see {@link JvmMetricsGroup}). */
    protected void extraRegister() {}
    protected void extraUnregister() {}
    protected void extraCollect() {}

    public final void register() {
        enabled = cfg.getBoolean(configRoot() + ".enabled", true);
        if (!enabled) return;

        String label = labelName();
        for (Desc<T> d : descriptions()) {
            if (!cfg.getBoolean(configRoot() + ".metrics." + d.cfgKey(), true)) continue;
            Gauge.Builder b = Gauge.builder().name(d.name()).help(d.help());
            if (label != null) b.labelNames(label);
            registeredDescs.add(new RegisteredDesc<>(d, b.register()));
        }

        for (GaugeCollector c : collectors()) {
            if (!cfg.getBoolean(configRoot() + ".metrics." + c.cfgKey(), true)) continue;
            Gauge.Builder b = Gauge.builder().name(c.name()).help(c.help());
            if (!c.labels().isEmpty()) b.labelNames(c.labels().toArray(new String[0]));
            registeredCollectors.add(new RegisteredCollector(c, b.register()));
        }

        extraRegister();
    }

    public final void unregister() {
        for (RegisteredDesc<T> r : registeredDescs) PrometheusRegistry.defaultRegistry.unregister(r.gauge());
        registeredDescs.clear();
        for (RegisteredCollector r : registeredCollectors) PrometheusRegistry.defaultRegistry.unregister(r.gauge());
        registeredCollectors.clear();
        extraUnregister();
    }

    public final void collect() {
        if (!enabled) return;

        // Clear labeled description gauges to drop stale label combinations (e.g. a player
        // who logged off). Unlabeled gauges are overwritten in place by .set() below.
        boolean hasLabel = labelName() != null;
        if (hasLabel) for (RegisteredDesc<T> r : registeredDescs) r.gauge().clear();

        for (T subject : subjects()) {
            String lv = hasLabel ? labelValue(subject) : null;
            for (RegisteredDesc<T> r : registeredDescs) {
                double v = r.desc().fn().applyAsDouble(subject);
                if (hasLabel) r.gauge().labelValues(lv).set(v);
                else r.gauge().set(v);
            }
        }

        for (RegisteredCollector r : registeredCollectors) {
            if (!r.collector().labels().isEmpty()) r.gauge().clear();
            r.collector().collect().accept(r.gauge());
        }

        extraCollect();
    }
}
