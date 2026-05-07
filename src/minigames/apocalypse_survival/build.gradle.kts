plugins {
    alias(libs.plugins.java)
}

loom {
    accessWidenerPath = file("src/main/resources/ap2-apocalypse-survival.accesswidener")
}

dependencies {
    api(project(":lib"))
}