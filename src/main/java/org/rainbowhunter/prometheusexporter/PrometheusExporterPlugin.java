package org.rainbowhunter.prometheusexporter;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.rainbowhunter.prometheusexporter.commands.PECommands;
import org.rainbowhunter.prometheusexporter.metrics.JvmMetricsGroup;
import org.rainbowhunter.prometheusexporter.metrics.MetricGroup;
import org.rainbowhunter.prometheusexporter.metrics.PlayerMetrics;
import org.rainbowhunter.prometheusexporter.metrics.ServerMetrics;
import org.rainbowhunter.prometheusexporter.metrics.WorldMetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrometheusExporterPlugin extends JavaPlugin {

    private HTTPServer httpServer;
    private BukkitTask collectionTask;
    private List<MetricGroup<?>> groups = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        buildGroups();
        groups.forEach(MetricGroup::register);

        int port = getConfig().getInt("metrics-port", 9940);
        try {
            httpServer = HTTPServer.builder().port(port).buildAndStart();
            getLogger().info("Prometheus metrics available on port " + port);
        } catch (IOException e) {
            getLogger().severe("Failed to start metrics HTTP server: " + e.getMessage());
        }

        startCollectionTask();

        new PECommands(this, this::reload).register();
    }

    @Override
    public void onDisable() {
        stopCollectionTask();
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    private void reload() {
        stopCollectionTask();
        groups.forEach(MetricGroup::unregister);

        if (httpServer != null) httpServer.stop();
        reloadConfig();
        int port = getConfig().getInt("metrics-port", 9940);
        try {
            httpServer = HTTPServer.builder().port(port).buildAndStart();
        } catch (IOException e) {
            getLogger().severe("Failed to restart metrics HTTP server: " + e.getMessage());
        }

        // JvmMetrics has no clean unregister path; toggling jvm_metrics.enabled requires a full server restart.
        buildGroups();
        groups.forEach(MetricGroup::register);
        startCollectionTask();
        getLogger().info("Configuration reloaded");
    }

    private void startCollectionTask() {
        long intervalTicks = getConfig().getLong("collection-interval-ticks", 20L);
        collectionTask = getServer().getScheduler()
                .runTaskTimer(this, this::collectMetrics, 0L, intervalTicks);
    }

    private void stopCollectionTask() {
        if (collectionTask != null) {
            collectionTask.cancel();
            collectionTask = null;
        }
    }

    private void buildGroups() {
        groups.clear();
        FileConfiguration cfg = getConfig();
        groups.add(new ServerMetrics(cfg));
        groups.add(new WorldMetrics(cfg));
        groups.add(new PlayerMetrics(cfg));
        groups.add(new JvmMetricsGroup(cfg));
    }

    private void collectMetrics() {
        for (MetricGroup<?> g : groups) g.collect();
    }
}
