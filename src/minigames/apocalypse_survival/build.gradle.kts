plugins {
    alias(libs.plugins.kotlin.jvm)
}

val javaVersion = libs.versions.java.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}

loom {
    accessWidenerPath = file("src/main/resources/ap2-apocalypse-survival.accesswidener")
}
