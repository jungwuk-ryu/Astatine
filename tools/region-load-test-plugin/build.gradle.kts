plugins {
    java
}

group = "io.multipaper.tools"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val repoRoot = projectDir.resolve("../..").normalize()
val localApiJar = repoRoot.resolve("shreddedpaper-api/build/libs")
    .takeIf { it.isDirectory }
    ?.listFiles()
    ?.filter { it.isFile && it.name.startsWith("shreddedpaper-api-") && it.extension == "jar" }
    ?.maxByOrNull { it.lastModified() }

dependencies {
    if (localApiJar != null) {
        compileOnly(files(localApiJar))
    } else {
        logger.lifecycle("Local shreddedpaper-api jar not found, falling back to remote Paper API")
    }
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("region-load-test-plugin")
}
