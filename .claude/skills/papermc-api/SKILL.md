---
name: papermc-api
description: >
  Reference for PaperMC 26.1.2 API patterns used in this Prometheus exporter plugin.
  Use this skill whenever working on any part of the plugin: collecting metrics (TPS,
  MSPT, players, worlds, chunks, entities), scheduling collection tasks, setting up the
  Prometheus HTTP endpoint, or wiring Gradle dependencies. Also use when the user asks
  how to get any server statistic from the Paper API, even if they don't mention
  "Prometheus" explicitly.
---

# PaperMC API Reference — Prometheus Exporter

## Gradle Setup

### Dependencies (`build.gradle.kts`)

```kotlin
plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"  // required to shade Prometheus lib
}

repositories {
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("io.prometheus:prometheus-metrics-core:1.6.1")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver:1.6.1")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm:1.6.1")  // optional: JVM heap/GC/threads
}

tasks.shadowJar {
    archiveClassifier.set("")  // replace the plain jar with the shaded one
}
```

The Prometheus client is **not** provided by the server, so it must be shaded into the plugin JAR. Without the Shadow plugin the server will throw `ClassNotFoundException` at runtime.

---

## Core Server Metrics API

```java
import org.bukkit.Bukkit;

// TPS — double[3]: [1-minute avg, 5-minute avg, 15-minute avg]
double[] tps = Bukkit.getTPS();

// MSPT — average milliseconds spent per server tick
double mspt = Bukkit.getAverageTickTime();

// Raw tick times — last 100 tick durations in nanoseconds
long[] tickTimes = Bukkit.getTickTimes();

// Current server tick number (monotonically increasing)
int currentTick = Bukkit.getCurrentTick();

// Online player count
int online  = Bukkit.getOnlinePlayers().size();
int maxSlots = Bukkit.getMaxPlayers();
```

---

## Per-World Metrics API

```java
for (World world : Bukkit.getWorlds()) {
    String name = world.getName();
    World.Environment env = world.getEnvironment(); // NORMAL, NETHER, THE_END

    int players      = world.getPlayerCount();
    int chunks       = world.getChunkCount();          // loaded chunks
    int entities     = world.getEntityCount();
    int tileEntities = world.getTileEntityCount();
    int tickingTE    = world.getTickableTileEntityCount();
}
```

Use `world.getName()` as a Prometheus label (e.g. `world="world"`, `world="world_nether"`).

---

## Per-Player Metrics API

```java
for (Player player : Bukkit.getOnlinePlayers()) {
    String name     = player.getName();
    int ping        = player.getPing();           // ms latency to client
    double health   = player.getHealth();         // 0.0–20.0 (default)
    int level       = player.getLevel();          // XP level
    GameMode mode   = player.getGameMode();       // SURVIVAL, CREATIVE, etc.
    World world     = player.getWorld();
}
```

Per-player metrics should use `player` as a label. Be mindful that high player counts can create high-cardinality label sets — consider aggregating (e.g. count per game mode) instead of labeling individual players if scale is a concern.

---

## Scheduling Metric Collection

**All Bukkit/Paper API calls must happen on the main server thread.** Use a synchronous repeating task to collect metrics into Prometheus gauges; Prometheus' HTTP thread then reads the gauges safely (Gauge operations are thread-safe).

```java
import org.bukkit.scheduler.BukkitTask;

// In onEnable():
BukkitTask collectionTask = getServer().getScheduler().runTaskTimer(
    this,
    this::collectMetrics,   // called on main thread
    0L,                     // initial delay (ticks)
    20L                     // period: 20 ticks = 1 second
);

// In onDisable():
collectionTask.cancel();
```

Do **not** use `runTaskTimerAsynchronously` to call Paper APIs — it will produce thread-safety warnings and may cause data corruption.

---

## Prometheus Client Setup (v1.6.x)

### Declare gauges (once, at class level or in `onEnable`)

```java
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.snapshots.Unit;

Gauge tpsGauge = Gauge.builder()
    .name("mc_tps")
    .help("Server TPS (1-minute average)")
    .unit(Unit.RATIO)
    .register();

Gauge msptGauge = Gauge.builder()
    .name("mc_mspt")
    .help("Average milliseconds per server tick")
    .unit(Unit.MILLISECONDS)
    .register();

// Per-world gauge: label dimension = "world"
Gauge chunkGauge = Gauge.builder()
    .name("mc_loaded_chunks")
    .help("Number of loaded chunks per world")
    .labelNames("world")
    .register();
```

### Update gauges in the collection task

```java
private void collectMetrics() {
    tpsGauge.set(Bukkit.getTPS()[0]);           // 1-minute TPS
    msptGauge.set(Bukkit.getAverageTickTime());

    for (World w : Bukkit.getWorlds()) {
        chunkGauge.labelValues(w.getName()).set(w.getChunkCount());
    }
}
```

### Start the HTTP server (in `onEnable`)

```java
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

HTTPServer httpServer = HTTPServer.builder()
    .port(getConfig().getInt("metrics-port", 9940))
    .buildAndStart();   // starts a daemon thread; non-blocking
```

### Stop it (in `onDisable`)

```java
httpServer.stop();
```

Prometheus scrapes `http://<server-ip>:9940/metrics` by default.

---

## Optional: JVM Metrics

```java
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;

// Call once in onEnable — registers heap, GC, thread, classloader gauges automatically
JvmMetrics.builder().register();
```

---

## Metric Naming Conventions

| Metric | Name | Type |
|---|---|---|
| TPS (1m/5m/15m) | `mc_tps` | Gauge |
| MSPT | `mc_mspt_milliseconds` | Gauge |
| Online players | `mc_players_online` | Gauge |
| Max player slots | `mc_players_max` | Gauge |
| Loaded chunks | `mc_loaded_chunks` (label: `world`) | Gauge |
| Entity count | `mc_entities` (label: `world`) | Gauge |
| Tile entities | `mc_tile_entities` (label: `world`) | Gauge |
| Player ping | `mc_player_ping_milliseconds` (label: `player`) | Gauge |

Prefix all metrics with `mc_`. Per-world metrics use a `world` label. Counters use `_total` suffix.

---

## `config.yml` defaults

```yaml
metrics-port: 9940
collection-interval-ticks: 20   # 20 ticks = 1 second
```

---

## Thread Safety Summary

| Operation | Thread | Safe? |
|---|---|---|
| `Bukkit.*` API calls | Main (sync task) | Yes |
| `Bukkit.*` API calls | Async task | **No** |
| `Gauge.set()` / `Counter.inc()` | Any | Yes |
| Prometheus HTTP scrape | HTTP daemon thread | Yes |
