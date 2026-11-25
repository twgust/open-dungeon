plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(projects.core.network)

    testImplementation(libs.kotlinx.coroutines.test)
}
