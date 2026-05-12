plugins {
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion = libs.versions.java.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}