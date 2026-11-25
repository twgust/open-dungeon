plugins {
    alias(libs.plugins.opendungeon.android.application)
    alias(libs.plugins.opendungeon.android.compose)
    alias(libs.plugins.opendungeon.metro)
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
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.feature.connect)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.metrox.android)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
