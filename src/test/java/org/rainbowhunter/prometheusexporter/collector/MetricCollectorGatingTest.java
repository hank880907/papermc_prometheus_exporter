package org.rainbowhunter.prometheusexporter.collector;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class MetricCollectorGatingTest {

    @Test
    void registersWhenGateAbsent() {
        SimpleGauge g = new SimpleGauge("k", "test_metric", "h", x -> x.set(1));
        g.register(new YamlConfiguration(), "root");
        assertThat(TestMetrics.registeredMetricNames()).contains("test_metric");
    }

    @Test
    void registersWhenGateTrue() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("root.metrics.k", true);
        SimpleGauge g = new SimpleGauge("k", "test_metric", "h", x -> x.set(1));
        g.register(cfg, "root");
        assertThat(TestMetrics.registeredMetricNames()).contains("test_metric");
    }

    @Test
    void doesNotRegisterWhenGateFalse() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("root.metrics.k", false);
        SimpleGauge g = new SimpleGauge("k", "test_metric", "h",
                x -> { throw new AssertionError("collect must not run when gated off"); });
        g.register(cfg, "root");
        g.collect();

        assertThat(TestMetrics.registeredMetricNames()).doesNotContain("test_metric");
    }

    @Test
    void unregisterIsNoOpWhenInactive() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("root.metrics.k", false);
        SimpleGauge g = new SimpleGauge("k", "test_metric", "h", x -> x.set(1));
        g.register(cfg, "root");
        g.unregister();
    }

}
