package dev.opendungeon.core.network.di

import dev.opendungeon.core.network.AppAttribution
import dev.opendungeon.core.network.openRouterHttpClient
import dev.opendungeon.core.network.openRouterJson
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** The [HttpClient] configured for OpenRouter: base URL, attribution headers, JSON and timeouts. */
@Qualifier
public annotation class OpenRouterHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
public object NetworkBindings {

    @Provides
    @SingleIn(AppScope::class)
    @OpenRouterHttpClient
    public fun provideOpenRouterHttpClient(attribution: AppAttribution): HttpClient =
        openRouterHttpClient(engine = OkHttp.create(), json = openRouterJson(), attribution = attribution)
}
