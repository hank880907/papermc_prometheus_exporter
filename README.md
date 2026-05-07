# Prometheus Exporter

A PaperMC plugin that exposes server metrics to Prometheus via an HTTP endpoint.

## Requirements

- PaperMC 26.1.2
- Java 25

## Configuration

`plugins/PrometheusExporter/config.yml`:

```yaml
metrics_port: 9940
collection_interval_ticks: 20
metric_prefix: "mc_"

server_metrics:
  enabled: true
  metrics:
    mspt_milliseconds: true
    tps: true
    tick_time_milliseconds: true
    players_online: true
    players_max: true
    current_tick: true
    worlds: true

world_metrics:
  enabled: true
  metrics:
    players: true
    loaded_chunks: true
    entities: true
    entities_by_type: true
    tile_entities: true
    ticking_tile_entities: true
    time: true
    storm: true
    thundering: true

player_metrics:
  enabled: true
  metrics:
    ping_milliseconds: true
    health: true
    max_health: true
    food_level: true
    saturation: true
    xp_level: true
    xp_progress: true
    total_experience: true
    flying: true
    gamemode: true

jvm_metrics:
  enabled: true
```

`metric_prefix` is applied to the plugin's Minecraft metrics. JVM metrics come from the Prometheus JVM collector and
keep their `jvm_` names.

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
      - targets: [ "<server-ip>:9940" ]
```

## Metrics

### Server

| Metric                      | Labels                       | Description                                     |
|-----------------------------|------------------------------|-------------------------------------------------|
| `mc_tps`                    | `window` (`1m`, `5m`, `15m`) | Server ticks per second                         |
| `mc_mspt_milliseconds`      | —                            | Average milliseconds per tick (last 100 ticks)  |
| `mc_tick_time_milliseconds` | `quantile` (`max`, `p95`)    | Tick duration quantiles over the last 100 ticks |
| `mc_players_online`         | —                            | Current online player count                     |
| `mc_players_max`            | —                            | Maximum player slots                            |
| `mc_current_tick`           | —                            | Monotonically increasing tick counter           |
| `mc_worlds`                 | —                            | Number of currently loaded worlds               |

### Per-world

| Metric                           | Labels          | Description                                                       |
|----------------------------------|-----------------|-------------------------------------------------------------------|
| `mc_world_players`               | `world`         | Players per world                                                 |
| `mc_world_loaded_chunks`         | `world`         | Loaded chunks per world                                           |
| `mc_world_entities`              | `world`         | Total entity count per world                                      |
| `mc_world_entities_by_type`      | `world`, `type` | Entity count broken down by type (e.g. `zombie`, `cow`) per world |
| `mc_world_tile_entities`         | `world`         | Tile entity count per world                                       |
| `mc_world_ticking_tile_entities` | `world`         | Ticking tile entity count per world                               |
| `mc_world_time`                  | `world`         | In-game clock (0–24000); tracks day/night cycle                   |
| `mc_world_storm`                 | `world`         | 1 if a storm is active, 0 otherwise                               |
| `mc_world_thundering`            | `world`         | 1 if a thunderstorm is active, 0 otherwise                        |

### Per-player

| Metric                        | Labels   | Description                                                                        |
|-------------------------------|----------|------------------------------------------------------------------------------------|
| `mc_player_ping_milliseconds` | `player` | Latency per player                                                                 |
| `mc_player_health`            | `player` | Current health per player                                                          |
| `mc_player_max_health`        | `player` | Max health per player (useful when custom attributes are applied)                  |
| `mc_player_food_level`        | `player` | Hunger level per player (0–20)                                                     |
| `mc_player_saturation`        | `player` | Saturation per player (0.0–20.0+)                                                  |
| `mc_player_xp_level`          | `player` | XP level per player                                                                |
| `mc_player_xp_progress`       | `player` | Fractional XP progress within current level (0.0–1.0)                              |
| `mc_player_total_experience`  | `player` | Total accumulated XP per player                                                    |
| `mc_player_flying`            | `player` | 1 if the player is flying, 0 otherwise                                             |
| `mc_player_gamemode`          | `player` | Numeric active gamemode (0 = survival, 1 = creative, 2 = adventure, 3 = spectator) |

### JVM

| Metric  | Labels  | Description                         |
|---------|---------|-------------------------------------|
| `jvm_*` | various | JVM heap, GC, threads, classloaders |

## Building

```bash
./gradlew build        # produces build/libs/prometheus-exporter-1.0.0.jar
./gradlew clean build  # clean rebuild
```

## Testing

```bash
./gradlew test             # unit tests (~1s, no Paper dependency)
./gradlew integrationTest  # boots a real Paper server, scrapes /metrics (~25s)
```

`integrationTest` downloads Paper 26.1.2 (~50 MB) to `build/paper-cache/` on first run and reuses it afterward. CI (
`.github/workflows/ci.yml`) runs both on every push and pull request — unit tests gate, integration is advisory.

# Development

This plugin is designed to be extensible.

## Exposing metrics from other plugins

To scrape data out of another plugin (EssentialsX, LuckPerms, Vault, ...), add a new `MetricGroup` that talks to that
plugin's API. Pick a collector kind that matches the value's shape:

| Shape                                             | Class             | Example                                          |
|---------------------------------------------------|-------------------|--------------------------------------------------|
| Single unlabeled value                            | `SimpleGauge`     | `mc_tps_current 19.87`                           |
| One value per subject with one label              | `LabeledGauge<T>` | `mc_player_ping_milliseconds{player="alice"} 78` |
| Multi-label, or label values from a single source | `MultiLabelGauge` | See below                                        |

### MultiLabelGauge Examples

arbitrary rows, multiple labels. Two shapes fit:

*One source fanned out across label values* — e.g. a `double[]` of TPS windows becoming three rows distinguished by
`window`:

```
mc_tps{window="1m"} 19.87
mc_tps{window="5m"} 19.92
mc_tps{window="15m"} 19.95
```

*Cross-product across multiple dimensions* — e.g. entity counts broken down by `world` × `type`. `LabeledGauge` only
supports one label, so anything with ≥2 dimensions goes here:

```
mc_world_entities_by_type{world="world",type="zombie"} 87
mc_world_entities_by_type{world="world",type="cow"} 23
mc_world_entities_by_type{world="world_nether",type="piglin"} 12
```
