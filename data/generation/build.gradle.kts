plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.kotlin.serialization)
}

// Koog stays an implementation detail: features and interactors only see :domain:generation.
dependencies {
    api(projects.domain.generation)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(libs.koog.prompt.executor.model)
    implementation(libs.koog.prompt.executor.openrouter.client)
    implementation(libs.koog.http.client.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
