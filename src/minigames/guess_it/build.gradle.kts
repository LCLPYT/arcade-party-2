plugins {
    alias(libs.plugins.java)
}

dependencies {
    api(project(":lib"))

    testImplementation(libs.mockito.core)
}