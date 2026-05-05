# CLAUDE.md

Java Prometheus metrics exporter plugin for PaperMC 26.1.2. Gradle (Kotlin DSL), Java 25.

**Goal:** expose every metric available through the Paper API — be as comprehensive as possible. No auth on the metrics endpoint needed.

- Plugin name: **Prometheus Exporter** (abbreviation: **PE**)
- Package: `org.rainbowhunter.prometheusexporter`
- Include per-player metrics (ping, health, gamemode, XP level) — high cardinality is acceptable.
- Include JVM metrics (`JvmMetrics.builder().register()` — heap, GC, threads, classloader).
- Changing the http server interface will require a full server restart.

## Build

```bash
./gradlew build        # JAR → build/libs/
./gradlew test
./gradlew clean build
```

## Non-obvious Constraints

- **Java 25** toolchain required — Paper 26.1.2 minimum.
- Use `paper-plugin.yml`, not `plugin.yml`. No `commands:` section — register via Brigadier API in code.
- Paper 26.1 dropped the internal remapper — no obfuscated NMS access.
- `MiniMessage` only — `ChatColor` is deprecated.

## Metrics and API Patterns

Use the `papermc-api` skill for: Gradle deps (including Shadow plugin for shading Prometheus), metrics collection API (TPS, MSPT, players, worlds, entities), Prometheus gauge/HTTP server setup, and scheduler patterns.
