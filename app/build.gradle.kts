plugins {
    alias(libs.plugins.opendungeon.android.application)
    alias(libs.plugins.opendungeon.android.compose)
    alias(libs.plugins.opendungeon.metro)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.opendungeon.app"

    defaultConfig {
        applicationId = "dev.opendungeon"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.credentials)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.data.generation)
    implementation(projects.data.models)
    implementation(projects.data.openrouter)
    implementation(projects.data.story)
    implementation(projects.feature.newstory.domain)
    implementation(projects.feature.newstory.ui)
    implementation(projects.feature.connect.domain)
    implementation(projects.feature.connect.ui)
    implementation(projects.feature.models.domain)
    implementation(projects.feature.models.ui)
    implementation(projects.feature.play.domain)
    implementation(projects.feature.play.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.metrox.android)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
