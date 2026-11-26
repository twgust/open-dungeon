plugins {
    alias(libs.plugins.opendungeon.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
