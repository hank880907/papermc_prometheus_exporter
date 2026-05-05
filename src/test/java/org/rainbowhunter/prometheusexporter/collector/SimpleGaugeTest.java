package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class SimpleGaugeTest {

    @Test
    void registerAndCollectEmitsValue() {
        SimpleGauge gauge = new SimpleGauge("temp", "test_temperature", "test help",
                g -> g.set(42.0));
        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();

        GaugeSnapshot snapshot = TestMetrics.findGauge("test_temperature");
        assertThat(snapshot.getDataPoints()).hasSize(1);
        assertThat(snapshot.getDataPoints().getFirst().getValue()).isEqualTo(42.0);
        assertThat(snapshot.getMetadata().getHelp()).isEqualTo("test help");
    }

    @Test
    void unregisterRemovesFromRegistry() {
        SimpleGauge gauge = new SimpleGauge("temp", "test_temperature", "h", g -> g.set(1));
        gauge.register(new YamlConfiguration(), "x");
        gauge.unregister();

        assertThat(TestMetrics.registeredMetricNames()).doesNotContain("test_temperature");
    }

    @Test
    void collectIsNoOpBeforeRegister() {
        SimpleGauge gauge = new SimpleGauge("temp", "test_temperature", "h",
                g -> { throw new AssertionError("collect ran while inactive"); });
        gauge.collect();
    }

    @Test
    void unregisterIsIdempotent() {
        SimpleGauge gauge = new SimpleGauge("temp", "test_temperature", "h", g -> g.set(1));
        gauge.register(new YamlConfiguration(), "x");
        gauge.unregister();
        gauge.unregister();
    }

}
