package org.rainbowhunter.prometheusexporter.metrics;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

public class JvmMetricsGroup extends MetricGroup<Void> {

    public JvmMetricsGroup(FileConfiguration cfg) {
        super(cfg);
    }

    @Override protected String configRoot()             { return "jvm_metrics"; }
    @Override protected Iterable<Void> subjects()       { return Collections.emptyList(); }
    @Override protected String labelName()              { return null; }
    @Override protected String labelValue(Void subject) { return null; }
    @Override protected List<Desc<Void>> descriptions() { return List.of(); }

    @Override
    protected void extraRegister() {
        JvmMetrics.builder().register();
    }

    // JvmMetrics registers collectors on the default registry with no clean unregister path;
    // reload() leaves them in place (matches the caveat documented in PECommands.java).
}
