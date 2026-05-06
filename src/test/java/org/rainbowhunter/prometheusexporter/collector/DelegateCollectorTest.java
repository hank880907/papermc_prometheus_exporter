package org.rainbowhunter.prometheusexporter.collector;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rainbowhunter.prometheusexporter.PrometheusRegistryCleanup;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(PrometheusRegistryCleanup.class)
class DelegateCollectorTest {

    @Test
    void registerRunsRunnableOnce() {
        AtomicInteger calls = new AtomicInteger();
        DelegateCollector dc = new DelegateCollector("jvm", calls::incrementAndGet);

        dc.register(new YamlConfiguration(), "x");
        assertThat(calls).hasValue(1);

        dc.collect();
        dc.unregister();
        assertThat(calls).hasValue(1);
    }

    @Test
    void registerRespectsGate() {
        AtomicInteger calls = new AtomicInteger();
        DelegateCollector dc = new DelegateCollector("jvm", calls::incrementAndGet);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("x.metrics.jvm", false);
        dc.register(cfg, "x");

        assertThat(calls).hasValue(0);
    }

    @Test
    void repeatedCollectorsWithSameKeyRegisterDelegateOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();

        new DelegateCollector("shared_jvm_test", calls::incrementAndGet)
                .register(new YamlConfiguration(), "x");
        new DelegateCollector("shared_jvm_test", calls::incrementAndGet)
                .register(new YamlConfiguration(), "x");

        assertThat(calls).hasValue(1);
    }

    @Test
    void failedRegistrationCanBeRetried() {
        AtomicInteger calls = new AtomicInteger();
        Runnable register = () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> new DelegateCollector("retry_jvm_test", register)
                .register(new YamlConfiguration(), "x"))
                .isInstanceOf(IllegalStateException.class);
        new DelegateCollector("retry_jvm_test", register)
                .register(new YamlConfiguration(), "x");

        assertThat(calls).hasValue(2);
    }
}
