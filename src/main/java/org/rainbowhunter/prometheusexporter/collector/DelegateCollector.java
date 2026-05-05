package org.rainbowhunter.prometheusexporter.collector;

/**
 * Adapter for libraries that register their own collectors on the default registry
 * (e.g. {@code JvmMetrics.builder().register()}). The library self-tracks values, so
 * unregister and collect are no-ops. As a consequence, toggling a delegated metric
 * off at runtime requires a server restart — the {@code /pe reload} caveat in
 * {@code PECommands} carries over.
 */
public final class DelegateCollector extends MetricCollector {

    private final String configKey;
    private final Runnable register;

    public DelegateCollector(String configKey, Runnable register) {
        this.configKey = configKey;
        this.register = register;
    }

    @Override public String configKey() { return configKey; }

    @Override protected void doRegister() { register.run(); }
    @Override protected void doUnregister() {}
    @Override protected void doCollect() {}
}
