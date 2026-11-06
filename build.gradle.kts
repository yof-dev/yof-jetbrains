import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// yof for JetBrains IDEs: IntelliJ Platform Gradle Plugin 2.x, Kotlin. The yof CLI is bundled as a resource
// (yof/yof-cli.cjs) from node_modules/@yof/cli, which CI installs at the version in gradle.properties (yofCliVersion).

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.yof"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "io.yof.jetbrains"
        name = "yof"
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
        changeNotes = provider { file("CHANGELOG.md").readText().substringAfter("## ").substringAfter("\n").substringBefore("\n## ").trim() }
    }
    // Plugin signing: the three values come from CI secrets, never from a file in this repository.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

val yofCli = layout.projectDirectory.file("node_modules/@yof/cli/dist/bin.cjs")

val yofCliVersion = providers.gradleProperty("yofCliVersion")

val checkYofCli by tasks.registering {
    val cliFile = yofCli.asFile
    val wanted = yofCliVersion
    doLast {
        if (!cliFile.exists()) {
            throw GradleException("The yof CLI is missing. Run: npm install --no-save @yof/cli@" + wanted.get())
        }
    }
}

tasks {
    processResources {
        dependsOn(checkYofCli)
        from(yofCli) {
            into("yof")
            rename { "yof-cli.cjs" }
        }
    }
    wrapper {
        gradleVersion = "8.10.2"
    }
}
