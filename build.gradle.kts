plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "2.0.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.1.0"
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
    mainModule.set("ch.muhmenthaler.valdb")
    mainClass.set("ch.muhmenthaler.valdb.ValDBApplication")
}

javafx {
    version = "25.0.3"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.3")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--enable-native-access=javafx.graphics")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "valdb"
    }
    jpackage {
        installerType = "exe"
        installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
    }
}
