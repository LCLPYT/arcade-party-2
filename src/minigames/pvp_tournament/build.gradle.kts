plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}

val javaVersion = libs.versions.java.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    api(project(":lib"))
}
