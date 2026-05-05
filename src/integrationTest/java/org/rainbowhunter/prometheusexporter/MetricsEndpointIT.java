package org.rainbowhunter.prometheusexporter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
class MetricsEndpointIT {

    private static final String PAPER_VERSION = "26.1.2";
    private static final int METRICS_PORT = 9940;
    private static final String METRICS_URL = "http://localhost:" + METRICS_PORT + "/metrics";
    private static final Pattern PAPER_JAR_URL = Pattern.compile(
            "\"url\"\\s*:\\s*\"(https://fill-data\\.papermc\\.io[^\"]+\\.jar)\"");

    private Process serverProcess;
    private Path workDir;
    private String metricsBody;

    @BeforeAll
    void startPaperAndScrapeOnce() throws Exception {
        Path pluginJar = Path.of(System.getProperty("pe.plugin.jar"));
        Path cacheDir = Path.of(System.getProperty("pe.paper.cache.dir"));
        if (!Files.exists(pluginJar)) {
            throw new IllegalStateException("plugin jar missing — run shadowJar: " + pluginJar);
        }

        Path paperJar = ensurePaperJar(cacheDir);

        workDir = Files.createTempDirectory("pe-it-");
        Path pluginsDir = workDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.copy(pluginJar, pluginsDir.resolve(pluginJar.getFileName()));

        Files.writeString(workDir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(workDir.resolve("server.properties"),
                "online-mode=false\n"
                        + "level-type=minecraft\\:flat\n"
                        + "motd=test\n"
                        + "max-players=20\n"
                        + "spawn-protection=0\n");

        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        serverProcess = new ProcessBuilder(javaBin, "-Xmx1G", "-jar", paperJar.toString(), "--nogui")
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();

        CountDownLatch ready = new CountDownLatch(1);
        Thread pump = new Thread(() -> drainStdout(serverProcess.getInputStream(), ready),
                "paper-stdout");
        pump.setDaemon(true);
        pump.start();

        if (!ready.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("Paper did not log 'Done (' within 60s; check workdir " + workDir);
        }
        // One full collection cycle past Done so values are populated (config: 20 ticks ≈ 1s).
        Thread.sleep(1500);
        metricsBody = scrapeOnce();
        Path debugDump = Path.of("build/integration-test-metrics.txt");
        Files.createDirectories(debugDump.getParent());
        Files.writeString(debugDump, metricsBody);
    }

    @AfterAll
    void stopPaper() throws Exception {
        if (serverProcess == null || !serverProcess.isAlive()) return;
        try (OutputStream stdin = serverProcess.getOutputStream()) {
            stdin.write("stop\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ignored) {
        }
        if (!serverProcess.waitFor(30, TimeUnit.SECONDS)) {
            serverProcess.destroyForcibly();
        }
    }

    @Test
    void endpointReturns200WithTextPlain() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(METRICS_URL))
                        .GET().build(),
                BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("content-type").orElse(""))
                .startsWith("text/plain");
    }

    @Test
    void serverMetricsDeclared() {
        assertThat(metricsBody).contains(
                "# TYPE mc_mspt_milliseconds gauge",
                "# TYPE mc_players_online gauge",
                "# TYPE mc_players_max gauge",
                "# TYPE mc_current_tick gauge",
                "# TYPE mc_worlds gauge",
                "# TYPE mc_tps gauge",
                "# TYPE mc_tick_time_milliseconds gauge");
    }

    @Test
    void worldMetricsDeclared() {
        // mc_world_entities_by_type is omitted intentionally: prometheus-metrics-core 1.x
        // does not render TYPE lines for label-emitting gauges that have zero data points,
        // and there are no entities in a fresh empty flat world.
        assertThat(metricsBody).contains(
                "# TYPE mc_world_players gauge",
                "# TYPE mc_world_loaded_chunks gauge",
                "# TYPE mc_world_entities gauge",
                "# TYPE mc_world_tile_entities gauge",
                "# TYPE mc_world_ticking_tile_entities gauge",
                "# TYPE mc_world_time gauge",
                "# TYPE mc_world_storm gauge",
                "# TYPE mc_world_thundering gauge");
    }

    @Test
    void noPlayerSeriesWithEmptyServer() {
        // Per-player gauges have nothing to register without an online player; the prom client
        // omits TYPE lines for empty label-emitting gauges, so this absence IS the shape check.
        for (String name : List.of(
                "mc_player_ping_milliseconds",
                "mc_player_health",
                "mc_player_xp_level",
                "mc_player_gamemode")) {
            assertThat(seriesCount(metricsBody, name))
                    .as("series count for %s", name)
                    .isZero();
        }
    }

    @Test
    void serverValuesAreSensible() {
        assertThat(unlabeledValue(metricsBody, "mc_players_online")).isZero();
        assertThat(unlabeledValue(metricsBody, "mc_worlds")).isGreaterThanOrEqualTo(1.0);
        assertThat(unlabeledValue(metricsBody, "mc_current_tick")).isPositive();

        double tps1m = labeledValue(metricsBody, "mc_tps", "window", "1m");
        assertThat(tps1m).isGreaterThan(0).isLessThanOrEqualTo(20.5);
    }

    @Test
    void worldValuesAreSensible() {
        // Default world is named "world". A fresh empty flat world has 0 connected players,
        // 0 entities, and 0 loaded chunks (no spawn protection, no players to keep chunks alive).
        double worldPlayers = labeledValue(metricsBody, "mc_world_players", "world", "world");
        assertThat(worldPlayers).isZero();

        double entities = labeledValue(metricsBody, "mc_world_entities", "world", "world");
        assertThat(entities).isZero();

        double time = labeledValue(metricsBody, "mc_world_time", "world", "world");
        assertThat(time).isBetween(0.0, 24000.0);
    }

    @Test
    void jvmMetricsArePresent() {
        assertThat(metricsBody).contains("# TYPE jvm_memory_used_bytes gauge");
        double heap = labeledValue(metricsBody, "jvm_memory_used_bytes", "area", "heap");
        assertThat(heap).isPositive();
    }

    private static void drainStdout(InputStream in, CountDownLatch ready) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("Done (")) ready.countDown();
            }
        } catch (IOException ignored) {
        }
    }

    private static String scrapeOnce() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(
                URI.create(METRICS_URL)).GET().build();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        Throwable last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> r = http.send(req, BodyHandlers.ofString());
                if (r.statusCode() == 200) return r.body();
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(250);
        }
        AssertionError err = new AssertionError("/metrics did not respond 200 within 60s");
        if (last != null) err.initCause(last);
        throw err;
    }

    private static int seriesCount(String body, String metricName) {
        int count = 0;
        for (String line : body.split("\\R")) {
            if (line.startsWith("#")) continue;
            if (line.startsWith(metricName + "{") || line.startsWith(metricName + " ")) count++;
        }
        return count;
    }

    private static double unlabeledValue(String body, String metricName) {
        Pattern p = Pattern.compile("(?m)^" + Pattern.quote(metricName) + "\\s+(\\S+)\\s*$");
        Matcher m = p.matcher(body);
        if (!m.find()) throw new AssertionError("metric not found: " + metricName);
        return Double.parseDouble(m.group(1));
    }

    private static double labeledValue(String body, String metricName, String labelName, String labelValue) {
        Pattern p = Pattern.compile(
                "(?m)^" + Pattern.quote(metricName) + "\\{([^}]*)\\}\\s+(\\S+)\\s*$");
        Matcher m = p.matcher(body);
        String wanted = labelName + "=\"" + labelValue + "\"";
        while (m.find()) {
            if (m.group(1).contains(wanted)) return Double.parseDouble(m.group(2));
        }
        throw new AssertionError(
                "no series for " + metricName + " with " + labelName + "=" + labelValue);
    }

    private static Path ensurePaperJar(Path cacheDir) throws Exception {
        Files.createDirectories(cacheDir);
        try (var entries = Files.list(cacheDir)) {
            Path cached = entries
                    .filter(p -> p.getFileName().toString().startsWith("paper-" + PAPER_VERSION + "-"))
                    .findFirst().orElse(null);
            if (cached != null) return cached;
        }

        HttpClient http = HttpClient.newHttpClient();
        String body = http.send(
                HttpRequest.newBuilder(URI.create(
                                "https://fill.papermc.io/v3/projects/paper/versions/" + PAPER_VERSION + "/builds"))
                        .GET().build(),
                BodyHandlers.ofString()).body();

        Matcher m = PAPER_JAR_URL.matcher(body);
        if (!m.find()) throw new IllegalStateException("could not locate paper jar URL in API response");
        String url = m.group(1);
        Path target = cacheDir.resolve(url.substring(url.lastIndexOf('/') + 1));

        try (InputStream in = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                BodyHandlers.ofInputStream()).body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
