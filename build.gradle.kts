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

val prometheusVersion = "1.6.1"
val paperApiVersion = "26.1.2.build.+"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation("io.prometheus:prometheus-metrics-core:$prometheusVersion")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver:$prometheusVersion")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm:$prometheusVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against a real Paper server."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    dependsOn(tasks.shadowJar)
    shouldRunAfter(tasks.test)

    systemProperty("pe.plugin.jar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("pe.paper.cache.dir", layout.buildDirectory.dir("paper-cache").get().asFile.absolutePath)
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("io.prometheus", "org.rainbowhunter.prometheusexporter.shaded.prometheus")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
