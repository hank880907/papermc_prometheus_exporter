# Prometheus Exporter

A PaperMC plugin that exposes server metrics to Prometheus via an HTTP endpoint.

## Requirements

- PaperMC 26.1.2
- Java 25

## Installation

1. Build the plugin: `./gradlew build`
2. Copy `build/libs/prometheus-exporter-1.0.0.jar` to your server's `plugins/` directory
3. Start (or restart) the server

The metrics endpoint starts automatically on port `9940`.

## Configuration

`plugins/PrometheusExporter/config.yml`:

```yaml
metrics-port: 9940
collection-interval-ticks: 20   # 20 ticks = 1 second
```

## Scraping

Point Prometheus at:

```
http://<server-ip>:9940/metrics
```

Example `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: minecraft
    static_configs:
      - targets: ["<server-ip>:9940"]
```

## Metrics

| Metric                        | Labels                       | Description                           |
| ----------------------------- | ---------------------------- | ------------------------------------- |
| `mc_tps`                      | `window` (`1m`, `5m`, `15m`) | Server ticks per second               |
| `mc_mspt_milliseconds`        | —                            | Average milliseconds per tick         |
| `mc_players_online`           | —                            | Current online player count           |
| `mc_players_max`              | —                            | Maximum player slots                  |
| `mc_current_tick`             | —                            | Monotonically increasing tick counter |
| `mc_world_players`            | `world`                      | Players per world                     |
| `mc_loaded_chunks`            | `world`                      | Loaded chunks per world               |
| `mc_entities`                 | `world`                      | Entity count per world                |
| `mc_tile_entities`            | `world`                      | Tile entity count per world           |
| `mc_ticking_tile_entities`    | `world`                      | Ticking tile entity count per world   |
| `mc_player_ping_milliseconds` | `player`                     | Latency per player                    |
| `mc_player_health`            | `player`                     | Health per player (0–20)              |
| `mc_player_xp_level`          | `player`                     | XP level per player                   |
| `mc_player_gamemode`          | `player`, `gamemode`         | Active gamemode (1 = active)          |
| `jvm_*`                       | various                      | JVM heap, GC, threads, classloaders   |

## Building

```bash
./gradlew build        # produces build/libs/prometheus-exporter-1.0.0.jar
./gradlew clean build  # clean rebuild
```
