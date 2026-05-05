package org.rainbowhunter.prometheusexporter.collector;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * One Prometheus metric. Subclasses own their register / unregister / collect logic;
 * the base class only gates on {@code <configRoot>.metrics.<configKey>} and tracks
 * whether the metric is currently active.
 */
public abstract class MetricCollector {

    private boolean active;

    public abstract String configKey();

    protected abstract void doRegister();
    protected abstract void doUnregister();
    protected abstract void doCollect();

    public final void register(FileConfiguration cfg, String configRoot) {
        if (cfg.getBoolean(configRoot + ".metrics." + configKey(), true)) {
            doRegister();
            active = true;
        }
    }

    public final void unregister() {
        if (active) {
            doUnregister();
            active = false;
        }
    }

    public final void collect() {
        if (active) doCollect();
    }
}
