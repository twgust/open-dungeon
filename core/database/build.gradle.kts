plugins {
    alias(libs.plugins.opendungeon.android.library)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    // Migration tests open each exported schema, so the tests see the schemas as assets.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Robolectric runs on JUnit 4; the vintage engine runs those tests on the JUnit Platform.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
