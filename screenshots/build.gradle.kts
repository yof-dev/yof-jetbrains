// Drives the IDE started by `gradle runIdeForUiTests` (plugin build) through JetBrains' Remote-Robot and saves the three
// JetBrains Marketplace listing screenshots. Used only by .github/workflows/screenshots.yml; see ../SCREENSHOTS.md.

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    application
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation("com.intellij.remoterobot:remote-robot:0.11.23")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "io.yof.jetbrains.screenshots.ShotsKt"
    // java.awt.Robot moves the mouse and captures the Xvfb screen from this process.
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=false")
}
