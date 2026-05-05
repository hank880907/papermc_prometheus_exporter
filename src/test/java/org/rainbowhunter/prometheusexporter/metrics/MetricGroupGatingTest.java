package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;
import org.rainbowhunter.prometheusexporter.collector.MetricCollector;
import org.rainbowhunter.prometheusexporter.collector.SimpleGauge;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class MetricGroupGatingTest {

    @Test
    void registersChildrenWhenGroupEnabledMissing() {
        new TestGroup(new YamlConfiguration()).register();
        assertThat(TestMetrics.registeredMetricNames()).contains("mc_test_a", "mc_test_b");
    }

    @Test
    void skipsAllChildrenWhenGroupDisabled() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("test_metrics.enabled", false);
        new TestGroup(cfg).register();

        assertThat(TestMetrics.registeredMetricNames()).doesNotContain("mc_test_a", "mc_test_b");
    }

    @Test
    void perMetricGateOverridesWithinEnabledGroup() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("test_metrics.enabled", true);
        cfg.set("test_metrics.metrics.b", false);
        new TestGroup(cfg).register();

        assertThat(TestMetrics.registeredMetricNames()).contains("mc_test_a").doesNotContain("mc_test_b");
    }

    @Test
    void unregisterRemovesAllChildren() {
        TestGroup group = new TestGroup(new YamlConfiguration());
        group.register();
        group.unregister();

        assertThat(TestMetrics.registeredMetricNames()).doesNotContain("mc_test_a", "mc_test_b");
    }

    private static final class TestGroup extends MetricGroup {
        TestGroup(YamlConfiguration cfg) { super(cfg); }
        @Override protected String configRoot() { return "test_metrics"; }
        @Override protected List<MetricCollector> metrics() {
            return List.of(
                    new SimpleGauge("a", "mc_test_a", "h", g -> g.set(1)),
                    new SimpleGauge("b", "mc_test_b", "h", g -> g.set(2)));
        }
    }
}
