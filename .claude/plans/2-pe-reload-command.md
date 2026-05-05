# Plan: `pe reload` console/in-game command

## Context

After landing the per-metric config flags, operators must currently restart the plugin (or the whole server) to apply changes to `config.yml`. A `pe reload` command lets operators re-read `config.yml` and rebuild the gauges in place — no restart, no dropped scrapes from a port flap.

Confirmed scope (from clarifying questions):
- **Reload covers**: per-metric flags + `collection-interval-ticks`.
- **Reload does NOT cover**: `metrics-port` (would force restarting the HTTP server) and `jvm_metrics.enabled` (the JVM bundle is registered once and there is no public API to unregister it).
- **Permission**: custom node `prometheusexporter.reload`, defaulted to `op`. Console always passes the `requires` predicate.

---

## Critical Files

- `src/main/java/org/rainbowhunter/prometheusexporter/PrometheusExporterPlugin.java` — register the command, refactor `onEnable()`, add reload logic.
- `src/main/resources/paper-plugin.yml` — declare the permission node.

---

## Brigadier Command Registration (Paper 26.1)

Confirmed pattern from Paper docs:

```java
getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    event.registrar().register(
        Commands.literal("pe")
            .then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("prometheusexporter.reload"))
                .executes(ctx -> {
                    reload();
                    ctx.getSource().getSender().sendRichMessage(
                        "<green>Prometheus Exporter config reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
            .build(),
        "Prometheus Exporter management"
    );
});
```

Imports needed:
- `io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents`
- `io.papermc.paper.command.brigadier.Commands`
- `com.mojang.brigadier.Command` (for `Command.SINGLE_SUCCESS`)

Console always satisfies `requires` because `ConsoleCommandSender.hasPermission(...)` returns `true` for any node. In-game players must have the `prometheusexporter.reload` node (granted to ops by default via `paper-plugin.yml`).

---

## Implementation Steps

### 1. Declare permission in `paper-plugin.yml`

Append:
```yaml
permissions:
  prometheusexporter.reload:
    description: Allows reloading Prometheus Exporter configuration
    default: op
```

### 2. Refactor: extract gauge registration

Pull the existing 200-line gauge-registration block out of `onEnable()` into a new private method `registerGauges()`. `onEnable()` calls it once after `loadConfigFlags()`. No behavior change — the body moves verbatim.

### 3. Add `unregisterGauges()` helper

Symmetric counterpart that unregisters every non-null gauge from the default `PrometheusRegistry` and nulls the field. Use a small helper to keep it terse:

```java
private Gauge clear(Gauge g) {
    if (g != null) PrometheusRegistry.defaultRegistry.unregister(g);
    return null;
}

private void unregisterGauges() {
    tpsGauge          = clear(tpsGauge);
    msptGauge         = clear(msptGauge);
    tickTimeMaxGauge  = clear(tickTimeMaxGauge);
    // ...one line per Gauge field (24 total)
}
```

Nulling is required because `collectXxxMetrics()` uses `gauge != null` as its enable check.

### 4. Add `reload()` method

```java
private void reload() {
    if (collectionTask != null) {
        collectionTask.cancel();
        collectionTask = null;
    }
    unregisterGauges();
    reloadConfig();          // Bukkit: re-read config.yml from disk
    loadConfigFlags();
    registerGauges();
    long intervalTicks = getConfig().getLong("collection-interval-ticks", 20L);
    collectionTask = getServer().getScheduler().runTaskTimer(
        this, this::collectMetrics, 0L, intervalTicks);
    getLogger().info("Configuration reloaded");
}
```

Order matters: cancel the task first so no `collectMetrics()` runs while gauges are mid-swap.

`metrics-port` is intentionally not re-read here — `httpServer` keeps running on the original port. The `JvmMetrics.builder().register()` call is also not re-run; JVM toggle requires a full plugin restart.

### 5. Register the command in `onEnable()`

Add the `getLifecycleManager().registerEventHandler(...)` block from the section above, after gauge/HTTP setup. The lambda calls the new `reload()` method.

---

## What is NOT reloadable (documented behavior)

| Setting | Reload? | Why |
|---|---|---|
| `server_metrics.*`, `world_metrics.*`, `player_metrics.*` | Yes | Gauges unregistered + re-registered |
| `collection-interval-ticks` | Yes | Task is cancelled and rescheduled |
| `metrics-port` | No | Would require stopping/restarting HTTPServer; risks dropping in-flight scrapes |
| `jvm_metrics.enabled` | No | The JVM bundle registers many internal collectors with no public unregister hook |

The command's success message can mention this caveat, e.g. `"<green>Reloaded. <gray>(metrics-port and jvm_metrics require full restart)"`.

---

## Verification

1. `./gradlew build` — must compile cleanly.
2. Start server, scrape `http://localhost:9940/metrics` — confirm baseline gauges present.
3. Edit `config.yml`: set `player_metrics.ping: false`. From console run `pe reload`. Re-scrape — `mc_player_ping_milliseconds` is gone; other metrics remain.
4. Edit `config.yml`: set `collection-interval-ticks: 100`. Run `pe reload`. Confirm gauge values now refresh every ~5 seconds instead of every second (watch `mc_current_tick`).
5. As a non-op player, attempt `/pe reload` — Brigadier should reject (command not visible / permission denied).
6. As an op player, `/pe reload` should succeed and chat-confirm.
7. Edit `config.yml`: set `metrics-port: 9999`. Run `pe reload`. Confirm metrics still on `:9940` (not `:9999`) — port change is correctly ignored until restart.
