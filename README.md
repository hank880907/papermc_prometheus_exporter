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

### Server

| Metric                          | Labels                       | Description                                          |
| ------------------------------- | ---------------------------- | ---------------------------------------------------- |
| `mc_tps`                        | `window` (`1m`, `5m`, `15m`) | Server ticks per second                              |
| `mc_mspt_milliseconds`          | —                            | Average milliseconds per tick (last 100 ticks)       |
| `mc_tick_time_max_milliseconds` | —                            | Max tick duration among the last 100 ticks           |
| `mc_tick_time_p95_milliseconds` | —                            | 95th-percentile tick duration among the last 100 ticks |
| `mc_players_online`             | —                            | Current online player count                          |
| `mc_players_max`                | —                            | Maximum player slots                                 |
| `mc_current_tick`               | —                            | Monotonically increasing tick counter                |
| `mc_worlds`                     | —                            | Number of currently loaded worlds                    |

### Per-world

| Metric                       | Labels          | Description                                              |
| ---------------------------- | --------------- | -------------------------------------------------------- |
| `mc_world_players`           | `world`         | Players per world                                        |
| `mc_loaded_chunks`           | `world`         | Loaded chunks per world                                  |
| `mc_entities`                | `world`         | Total entity count per world                             |
| `mc_entities_by_category`    | `world`, `category` | Entity count broken down by category (monsters, animals, water_creatures, ambient, misc) |
| `mc_tile_entities`           | `world`         | Tile entity count per world                              |
| `mc_ticking_tile_entities`   | `world`         | Ticking tile entity count per world                      |
| `mc_world_time`              | `world`         | In-game clock (0–24000); tracks day/night cycle          |
| `mc_world_storm`             | `world`         | 1 if a storm is active, 0 otherwise                      |
| `mc_world_thundering`        | `world`         | 1 if a thunderstorm is active, 0 otherwise               |

### Per-player

| Metric                        | Labels           | Description                                    |
| ----------------------------- | ---------------- | ---------------------------------------------- |
| `mc_player_ping_milliseconds` | `player`         | Latency per player                             |
| `mc_player_health`            | `player`         | Current health per player                      |
| `mc_player_max_health`        | `player`         | Max health per player (useful when custom attributes are applied) |
| `mc_player_food_level`        | `player`         | Hunger level per player (0–20)                 |
| `mc_player_saturation`        | `player`         | Saturation per player (0.0–20.0+)              |
| `mc_player_xp_level`          | `player`         | XP level per player                            |
| `mc_player_xp_progress`       | `player`         | Fractional XP progress within current level (0.0–1.0) |
| `mc_player_total_experience`  | `player`         | Total accumulated XP per player                |
| `mc_player_flying`            | `player`         | 1 if the player is flying, 0 otherwise         |
| `mc_player_gamemode`          | `player`, `gamemode` | Active gamemode (1 = active, 0 = inactive) |

### JVM

| Metric  | Labels  | Description                           |
| ------- | ------- | ------------------------------------- |
| `jvm_*` | various | JVM heap, GC, threads, classloaders   |

## Building

```bash
./gradlew build        # produces build/libs/prometheus-exporter-1.0.0.jar
./gradlew clean build  # clean rebuild
```
