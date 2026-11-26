plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.metro)
}

dependencies {
    implementation(projects.core.credentials)
    implementation(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
