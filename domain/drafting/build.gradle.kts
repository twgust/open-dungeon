plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.kotlin.serialization)
}

// Writing parts of a story with a model: which model drafts, the prompts, and the use cases that turn a
// draft into a premise or a cast. Story types meet generation here, so neither module knows the other.
dependencies {
    api(projects.domain.story)
    api(projects.domain.generation)
    api(projects.domain.openrouter)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain.openrouter))
}

// Prompt goldens: `./gradlew :domain:drafting:test -PupdateGoldens` rewrites them after a deliberate change.
tasks.test {
    systemProperty("goldens.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.path)
    systemProperty("goldens.update", providers.gradleProperty("updateGoldens").isPresent)
}
