plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
}

// Connecting a key, with no Android in reach: the UI module can only call what ConnectInteractor exposes.
dependencies {
    api(projects.domain.openrouter)
    api(projects.domain.story)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain.openrouter))
}
