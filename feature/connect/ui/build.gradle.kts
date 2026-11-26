plugins {
    alias(libs.plugins.opendungeon.android.feature)
}

dependencies {
    implementation(projects.feature.connect.domain)
    implementation(libs.metrox.viewmodel.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
