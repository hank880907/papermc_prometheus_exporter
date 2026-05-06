package org.rainbowhunter.prometheusexporter.collector;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapter for libraries that register their own collectors on the default registry
 * (e.g. {@code JvmMetrics.builder().register()}). The library self-tracks values, so
 * unregister and collect are no-ops. As a consequence, toggling a delegated metric
 * off at runtime requires a server restart — the {@code /pe reload} caveat in
 * {@code PECommands} carries over.
 */
public final class DelegateCollector extends MetricCollector {

    private static final Set<String> REGISTERED_KEYS = new HashSet<>();

    private final String configKey;
    private final Runnable register;

    public DelegateCollector(String configKey, Runnable register) {
        this.configKey = configKey;
        this.register = register;
    }

    @Override public String configKey() { return configKey; }

    @Override protected void doRegister() {
        synchronized (REGISTERED_KEYS) {
            if (REGISTERED_KEYS.contains(configKey)) return;
            register.run();
            REGISTERED_KEYS.add(configKey);
        }
    }

    @Override protected void doUnregister() {}
    @Override protected void doCollect() {}
}
