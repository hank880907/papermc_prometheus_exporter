package org.rainbowhunter.prometheusexporter;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class PrometheusRegistryCleanup implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        PrometheusRegistry.defaultRegistry.clear();
    }
}
