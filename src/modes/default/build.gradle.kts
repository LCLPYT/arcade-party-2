plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion: Int = libs.versions.java.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    implementation(libs.json.config4j)
    implementation(libs.translations4j)
}