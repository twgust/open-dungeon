plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.kotlin.serialization)
}

// The wizard's rules, with no Android in reach: the UI module can only call what StoryWizard exposes.
dependencies {
    api(projects.domain.drafting)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain.openrouter))
}
