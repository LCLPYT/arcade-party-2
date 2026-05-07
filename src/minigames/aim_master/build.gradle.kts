plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.fabric.loom)
}

dependencies {
    api(project(":lib"))
}