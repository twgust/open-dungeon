plugins {
    alias(libs.plugins.opendungeon.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
}
