plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.mockito.core)
}