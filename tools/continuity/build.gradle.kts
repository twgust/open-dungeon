plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.kotlin.serialization)
    application
}

// A developer tool, never shipped: it drives the app's own generation stack against OpenRouter and
// measures how well a context policy keeps a long story continuous. See README.md in this module.
application {
    mainClass = "dev.opendungeon.tools.continuity.MainKt"
    applicationName = "continuity"
}

tasks.named<JavaExec>("run") {
    // Paths on the command line are read from the repository root, the directory the README's commands run from.
    workingDir = rootDir
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.credentials)
    implementation(projects.core.network)
    implementation(projects.domain.generation)
    implementation(projects.data.generation)
    implementation(projects.data.openrouter)
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Koog logs through SLF4J; without a binding it warns on every start, and the harness has nothing to log.
    runtimeOnly(libs.slf4j.nop)

    testImplementation(testFixtures(projects.domain.openrouter))
    testImplementation(libs.kotlinx.coroutines.test)
}
