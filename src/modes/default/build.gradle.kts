plugins {
    alias(libs.plugins.java)
}

dependencies {
    api(project(":lib"))

    implementation(libs.json.config4j)
    implementation(libs.translations4j)
}