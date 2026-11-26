plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
}

dependencies {
    api(projects.domain.openrouter)
    implementation(projects.core.credentials)
    implementation(projects.core.network)

    testImplementation(libs.kotlinx.coroutines.test)
}
