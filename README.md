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

| Metric                      | Labels                         | Description                                         |
| --------------------------- | ------------------------------ | --------------------------------------------------- |
| `mc_tps`                    | `window` (`1m`, `5m`, `15m`)   | Server ticks per second                             |
| `mc_mspt_milliseconds`      | —                              | Average milliseconds per tick (last 100 ticks)      |
| `mc_tick_time_milliseconds` | `quantile` (`max`, `p95`)      | Tick duration quantiles over the last 100 ticks     |
| `mc_players_online`         | —                              | Current online player count                         |
| `mc_players_max`            | —                              | Maximum player slots                                |
| `mc_current_tick`           | —                              | Monotonically increasing tick counter               |
| `mc_worlds`                 | —                              | Number of currently loaded worlds                   |

### Per-world

| Metric                           | Labels          | Description                                              |
| -------------------------------- | --------------- | -------------------------------------------------------- |
| `mc_world_players`               | `world`         | Players per world                                        |
| `mc_world_loaded_chunks`         | `world`         | Loaded chunks per world                                  |
| `mc_world_entities`              | `world`         | Total entity count per world                             |
| `mc_world_entities_by_type`      | `world`, `type` | Entity count broken down by type (e.g. `zombie`, `cow`) per world |
| `mc_world_tile_entities`         | `world`         | Tile entity count per world                              |
| `mc_world_ticking_tile_entities` | `world`         | Ticking tile entity count per world                      |
| `mc_world_time`                  | `world`         | In-game clock (0–24000); tracks day/night cycle          |
| `mc_world_storm`                 | `world`         | 1 if a storm is active, 0 otherwise                      |
| `mc_world_thundering`            | `world`         | 1 if a thunderstorm is active, 0 otherwise               |

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
| `mc_player_gamemode`          | `player`         | Numeric active gamemode (0 = survival, 1 = creative, 2 = adventure, 3 = spectator) |

### JVM

| Metric  | Labels  | Description                           |
| ------- | ------- | ------------------------------------- |
| `jvm_*` | various | JVM heap, GC, threads, classloaders   |

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

`integrationTest` downloads Paper 26.1.2 (~50 MB) to `build/paper-cache/` on first run and reuses it afterward. CI (`.github/workflows/ci.yml`) runs both on every push and pull request — unit tests gate, integration is advisory.

## Exposing metrics from other plugins

To scrape data out of another plugin (EssentialsX, LuckPerms, Vault, ...), add a new `MetricGroup` that talks to that plugin's API. Pick a collector kind that matches the value's shape:

| Shape                                             | Class             |
| ------------------------------------------------- | ----------------- |
| Single unlabeled value                            | `SimpleGauge`     |
| One value per subject with one label              | `LabeledGauge<T>` |
| Multi-label, or label values from a single source | `MultiLabelGauge` |

#### What each one emits

`SimpleGauge` — one row, no labels. A single global fact about the server.

```
mc_tps_current 19.87
mc_players_online 4
```

`LabeledGauge<T>` — one row per subject, single label. Source shape is an `Iterable<T>` plus a label function and a value function. The gauge is cleared each collect so subjects that disappear (logged-off players, unloaded worlds) stop emitting.

```
mc_player_ping_milliseconds{player="hank"} 42
mc_player_ping_milliseconds{player="alice"} 78

mc_world_loaded_chunks{world="world"} 441
mc_world_loaded_chunks{world="world_nether"} 132
```

`MultiLabelGauge` — arbitrary rows, multiple labels. Two shapes fit:

*One source fanned out across label values* — e.g. a `double[]` of TPS windows becoming three rows distinguished by `window`:

```
mc_tps{window="1m"} 19.87
mc_tps{window="5m"} 19.92
mc_tps{window="15m"} 19.95
```

*Cross-product across multiple dimensions* — e.g. entity counts broken down by `world` × `type`. `LabeledGauge` only supports one label, so anything with ≥2 dimensions goes here:

```
mc_world_entities_by_type{world="world",type="zombie"} 87
mc_world_entities_by_type{world="world",type="cow"} 23
mc_world_entities_by_type{world="world_nether",type="piglin"} 12
```

Mental model: `SimpleGauge` is one number, `LabeledGauge` is `{subject -> number}`, `MultiLabelGauge` is a table with multiple key columns `{(k1, k2, ...) -> number}`.

### Example: expose EssentialsX balance as `mc_essentials_balance`

1. Soft-depend on the plugin in `src/main/resources/paper-plugin.yml` so PE still loads on servers without it:

   ```yaml
   dependencies:
     server:
       Essentials:
         load: BEFORE
         required: false
         join-classpath: true
   ```

2. Add the API as a `compileOnly` dependency in `build.gradle.kts` so you can call it at compile time without bundling it.

3. Subclass `MetricGroup`. Look the plugin up once at construction; if it's missing, return an empty `metrics()` so the group registers nothing:

   ```
   public class EssentialsMetrics extends MetricGroup {
       private final Essentials essentials;

       public EssentialsMetrics(FileConfiguration cfg) {
           super(cfg);
           Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
           this.essentials = (p instanceof Essentials e) ? e : null;
       }

       @Override protected String configRoot() { return "essentials_metrics"; }

       @Override protected List<MetricCollector> metrics() {
           if (essentials == null) return List.of();
           return List.of(
               new LabeledGauge<>(
                   "balance", "mc_essentials_balance", "Player economy balance", "player",
                   Bukkit::getOnlinePlayers, Player::getName,
                   p -> essentials.getUser(p).getMoney().doubleValue()
               )
           );
       }
   }
   ```

4. Register the group in `PrometheusExporterPlugin.buildGroups()`:

   ```
   groups.add(new EssentialsMetrics(cfg));
   ```

5. Add the stanza to `src/main/resources/config.yml`:

   ```yaml
   essentials_metrics:
     enabled: true
     metrics:
       balance: true
   ```

**Config-key convention:** strip the `mc_` prefix, then strip the group-singular prefix if present. `mc_essentials_balance` → `balance`; `mc_luckperms_group_size` → `group_size`. Each collector declares its key explicitly — the convention is for the author, not auto-derivation.
