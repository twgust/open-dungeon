plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.android.compose)
}

// Fonts are bundled under the SIL Open Font License 1.1; see licenses/.
dependencies {
    api(libs.compose.material3)
    api(libs.compose.ui)
}
