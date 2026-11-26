plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
    api(libs.compose.material3)
    api(libs.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
