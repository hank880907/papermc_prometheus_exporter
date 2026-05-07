package org.rainbowhunter.prometheusexporter.metrics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;
import org.rainbowhunter.prometheusexporter.TestMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PrometheusRegistryCleanup.class)
class MetricPrefixTest {

    @Test
    void serverMetricsUseConfiguredPrefix() {
        new ServerMetrics(new YamlConfiguration(), "custom_").register();

        assertThat(TestMetrics.registeredMetricNames())
                .contains("custom_players_online", "custom_tps")
                .doesNotContain("mc_players_online", "mc_tps");
    }

    @Test
    void worldMetricsUseConfiguredPrefix() {
        new WorldMetrics(new YamlConfiguration(), "custom_").register();

        assertThat(TestMetrics.registeredMetricNames())
                .contains("custom_world_players", "custom_world_entities_by_type")
                .doesNotContain("mc_world_players", "mc_world_entities_by_type");
    }

    @Test
    void playerMetricsUseConfiguredPrefix() {
        new PlayerMetrics(new YamlConfiguration(), "custom_").register();

        assertThat(TestMetrics.registeredMetricNames())
                .contains("custom_player_health")
                .doesNotContain("mc_player_health");
    }

    @Test
    void nullPrefixFallsBackToDefault() {
        new ServerMetrics(new YamlConfiguration(), null).register();

        assertThat(TestMetrics.registeredMetricNames()).contains("mc_players_online");
    }
}
