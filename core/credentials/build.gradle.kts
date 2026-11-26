plugins {
    alias(libs.plugins.opendungeon.jvm.library)
}

// The contract for where the player's key is kept. Data modules read it; :core:datastore implements it.
dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
