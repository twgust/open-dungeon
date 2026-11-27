plugins {
    alias(libs.plugins.opendungeon.android.feature)
}

dependencies {
    implementation(projects.feature.play.domain)
    implementation(libs.androidx.activity.compose)
    implementation(libs.metrox.viewmodel.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
