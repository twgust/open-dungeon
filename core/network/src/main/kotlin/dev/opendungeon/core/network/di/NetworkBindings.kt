package dev.opendungeon.core.network.di

import dev.opendungeon.core.network.AppAttribution
import dev.opendungeon.core.network.openRouterBaseClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * The one [HttpClient] behind every call to OpenRouter, REST and generation alike: a single OkHttp engine
 * with the attribution headers. Each caller specialises it; see [openRouterBaseClient].
 */
@Qualifier
public annotation class OpenRouterHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
public object NetworkBindings {

    @Provides
    @SingleIn(AppScope::class)
    @OpenRouterHttpClient
    public fun provideOpenRouterHttpClient(attribution: AppAttribution): HttpClient =
        openRouterBaseClient(engine = OkHttp.create(), attribution = attribution)
}
