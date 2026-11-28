plugins {
    alias(libs.plugins.opendungeon.jvm.library)
}

// The project's own detekt rules, run by every module's `detekt` through the quality convention. Never shipped.
dependencies {
    // detekt supplies its API when it loads the rules, so the rules never bring their own copy.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.test)
}
