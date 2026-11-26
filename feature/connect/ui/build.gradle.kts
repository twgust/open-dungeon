plugins {
    alias(libs.plugins.opendungeon.android.feature)
}

dependencies {
    implementation(projects.domain.openrouter)
    implementation(libs.metrox.viewmodel.compose)
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
