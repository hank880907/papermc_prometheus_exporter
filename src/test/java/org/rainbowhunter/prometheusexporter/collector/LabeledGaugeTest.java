package org.rainbowhunter.prometheusexporter.collector;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class LabeledGaugeTest {

    @Test
    void emitsOneDataPointPerSubject() {
        List<String> subjects = new ArrayList<>(List.of("alice", "bob", "charlie"));
        LabeledGauge<String> gauge = new LabeledGauge<>(
                "ping", "test_ping", "help", "player",
                () -> subjects, s -> s, String::length);

        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();

        Map<String, Double> byPlayer = byLabel("test_ping", "player");
        assertThat(byPlayer).containsOnly(
                Map.entry("alice", 5.0),
                Map.entry("bob", 3.0),
                Map.entry("charlie", 7.0));
    }

    @Test
    void clearOnCollectDropsStaleLabels() {
        List<String> subjects = new ArrayList<>(List.of("alice", "bob"));
        LabeledGauge<String> gauge = new LabeledGauge<>(
                "ping", "test_ping", "h", "player",
                () -> subjects, s -> s, String::length);

        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();
        assertThat(byLabel("test_ping", "player")).containsOnlyKeys("alice", "bob");

        subjects.remove("alice");
        gauge.collect();
        assertThat(byLabel("test_ping", "player")).containsOnlyKeys("bob");
    }

    @Test
    void emptySubjectsCollectsNoDataPoints() {
        LabeledGauge<String> gauge = new LabeledGauge<>(
                "ping", "test_ping", "h", "player",
                List::of, s -> s, String::length);
        gauge.register(new YamlConfiguration(), "x");
        gauge.collect();

        assertThat(TestMetrics.findGauge("test_ping").getDataPoints()).isEmpty();
    }

    private static Map<String, Double> byLabel(String name, String label) {
        return TestMetrics.findGauge(name).getDataPoints().stream()
                .collect(Collectors.toMap(
                        d -> d.getLabels().get(label),
                        GaugeDataPointSnapshot::getValue));
    }
}
