import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.19"
}

paperweight {
    upstreams.register("purpur") {
        repo = github("PurpurMC", "Purpur")
        ref = providers.gradleProperty("purpurRef")

        patchFile {
            path = "purpur-server/build.gradle.kts"
            outputFile = file("shreddedpaper-server/build.gradle.kts")
            patchFile = file("shreddedpaper-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "purpur-api/build.gradle.kts"
            outputFile = file("shreddedpaper-api/build.gradle.kts")
            patchFile = file("shreddedpaper-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("shreddedpaper-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("purpurApi") {
            upstreamPath = "purpur-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("shreddedpaper-api/purpur-patches")
            outputDir = file("purpur-api")
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val defaultTestForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    if (name == "shreddedpaper-server") {
        tasks.withType<Jar>().configureEach {
            archiveBaseName.set("astatine-server")
        }
        plugins.withId("io.papermc.paperweight.core") {
            tasks.withType<io.papermc.paperweight.tasks.CreateBundlerJar>().configureEach {
                val mappingName = if (name.contains("Mojmap")) "mojmap" else "reobf"
                outputZip.set(project.layout.buildDirectory.file("libs/astatine-bundler-${project.version}-$mappingName.jar"))
            }
            tasks.withType<io.papermc.paperweight.tasks.CreatePaperclipJar>().configureEach {
                val mappingName = if (name.contains("Mojmap")) "mojmap" else "reobf"
                outputZip.set(project.layout.buildDirectory.file("libs/astatine-paperclip-${project.version}-$mappingName.jar"))
            }
        }
    }
    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
        options.isIncremental = true
        options.forkOptions.memoryInitialSize = "512m"
        options.compilerArgs.add("--enable-preview")
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        maxParallelForks = providers.gradleProperty("astatine.test.maxParallelForks")
            .orElse(providers.gradleProperty("shreddedpaper.test.maxParallelForks"))
            .map(String::toInt)
            .getOrElse(defaultTestForks)
        systemProperty("net.bytebuddy.experimental", "true")
        jvmArgs("--enable-preview")
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            /*
            maven("https://repo.papermc.io/repository/maven-snapshots/") {
                name = "paperSnapshots"
                credentials(PasswordCredentials::class)
            }
             */
        }
    }
}
