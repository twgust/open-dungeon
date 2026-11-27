plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    alias(libs.plugins.opendungeon.metro)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
