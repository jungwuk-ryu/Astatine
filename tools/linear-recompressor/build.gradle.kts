plugins {
    application
}

dependencies {
    implementation("com.github.luben:zstd-jni:1.5.7-7")
    implementation("at.yawk.lz4:lz4-java:1.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("net.openhft:zero-allocation-hashing:0.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

application {
    mainClass = "io.astatine.tools.linear.LinearRecompressorMain"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
