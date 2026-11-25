plugins {
    alias(libs.plugins.opendungeon.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.metrox.viewmodel.compose)
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
