plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}

val javaVersion = libs.versions.java.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
}

loom {
    accessWidenerPath = file("src/main/resources/ap2-lib.accesswidener")
}

dependencies {
    implementation(libs.json.config4j)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.translations4j)
}