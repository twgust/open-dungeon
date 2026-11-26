plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.metro)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

// The catalog's on-device cache and the player's model setup, in front of the network catalog.
dependencies {
    api(projects.domain.openrouter)
    implementation(projects.core.database)
    implementation(projects.data.openrouter)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Robolectric runs on JUnit 4; the vintage engine runs those tests on the JUnit Platform.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
