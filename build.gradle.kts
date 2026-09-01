plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "ch.muhmenthaler.valdb"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val junitVersion = "6.0.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("ch.muhmenthaler.valdb.Launcher")
}

javafx {
    version = "25.0.3"
    modules = listOf("javafx.controls", "javafx.fxml")
    // No 'platform' set on purpose: the plugin auto-detects the OS Gradle
    // is running on and pulls the matching native jars (win / linux / mac).
    // That's exactly why the fat jar below must be built separately on
    // each CI runner rather than once and reused.
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.3")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

// --- Fat jar for jpackage -------------------------------------------------
tasks.register<Jar>("customFatJar") {
    group = "distribution"
    description = "Assembles an uber-jar (app + all runtime deps) for jpackage."

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    archiveFileName.set("ValDB.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    with(tasks.jar.get() as CopySpec)
}

tasks.named("build") {
    dependsOn("customFatJar")
}

tasks.register("printAppVersion") {
    doLast {
        println(version.toString().substringBefore("-"))
    }
}