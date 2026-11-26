plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
}

// Picking, tuning and trying a model, with no Android in reach: the UI module only calls these interactors.
dependencies {
    api(projects.domain.openrouter)
    api(projects.domain.generation)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain.openrouter))
}
