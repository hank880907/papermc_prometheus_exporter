package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class MultiLabelGaugeTest {

    @Test
    void emitsMultipleLabelCombinations() {
        MultiLabelGauge gauge = new MultiLabelGauge(
                "entities_by_type", "test_entities_by_type", "h",
                List.of("world", "type"),
                g -> {
                    g.labelValues("overworld", "zombie").set(3);
                    g.labelValues("overworld", "cow").set(7);
                    g.labelValues("nether", "ghast").set(1);
                });
        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();

        GaugeSnapshot snap = TestMetrics.findGauge("test_entities_by_type");
        assertThat(snap.getDataPoints()).hasSize(3);
        assertThat(value(snap, "overworld", "zombie")).isEqualTo(3.0);
        assertThat(value(snap, "overworld", "cow")).isEqualTo(7.0);
        assertThat(value(snap, "nether", "ghast")).isEqualTo(1.0);
    }

    @Test
    void clearOnCollectDropsStaleLabelCombinations() {
        AtomicReference<Boolean> includeGhast = new AtomicReference<>(true);
        MultiLabelGauge gauge = new MultiLabelGauge(
                "entities_by_type", "test_entities_by_type", "h",
                List.of("world", "type"),
                g -> {
                    g.labelValues("overworld", "zombie").set(3);
                    if (includeGhast.get()) g.labelValues("nether", "ghast").set(1);
                });
        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();
        assertThat(TestMetrics.findGauge("test_entities_by_type").getDataPoints()).hasSize(2);

        includeGhast.set(false);
        gauge.collect();
        GaugeSnapshot snap = TestMetrics.findGauge("test_entities_by_type");
        assertThat(snap.getDataPoints()).hasSize(1);
        assertThat(value(snap, "overworld", "zombie")).isEqualTo(3.0);
    }

    private static double value(GaugeSnapshot snap, String world, String type) {
        for (GaugeDataPointSnapshot d : snap.getDataPoints()) {
            if (world.equals(d.getLabels().get("world")) && type.equals(d.getLabels().get("type"))) {
                return d.getValue();
            }
        }
        throw new AssertionError("no data point for (" + world + ", " + type + ")");
    }
}
