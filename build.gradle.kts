plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

group = "org.rainbowhunter"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("io.prometheus:prometheus-metrics-core:1.6.1")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver:1.6.1")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm:1.6.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.prometheus", "org.rainbowhunter.prometheusexporter.shaded.prometheus")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
