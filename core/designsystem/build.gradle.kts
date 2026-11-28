plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.android.compose)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Fonts are bundled under the SIL Open Font License 1.1; see licenses/.
dependencies {
    api(libs.compose.material3)
    api(libs.compose.ui)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    // Robolectric runs on JUnit 4; the vintage engine runs those tests on the JUnit Platform.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.compose.ui.test.manifest)
}
