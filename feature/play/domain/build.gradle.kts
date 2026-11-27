plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
}

// Play's rules, with no Android in reach: the UI module can only call what PlaySession exposes.
dependencies {
    api(projects.domain.drafting)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain.openrouter))
}
