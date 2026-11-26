package dev.opendungeon.data.generation.di

import ai.koog.prompt.executor.model.PromptExecutor
import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.network.di.OpenRouterHttpClient
import dev.opendungeon.data.generation.openRouterPromptExecutor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first

/** Koog's executor for OpenRouter, authenticated with whatever key is stored at the time of each request. */
@Qualifier
public annotation class OpenRouterExecutor

@BindingContainer
@ContributesTo(AppScope::class)
public object GenerationBindings {

    @Provides
    @SingleIn(AppScope::class)
    @OpenRouterExecutor
    public fun provideOpenRouterExecutor(
        keys: ApiKeyStore,
        @OpenRouterHttpClient baseClient: HttpClient,
    ): PromptExecutor = openRouterPromptExecutor(baseClient = baseClient, apiKey = { keys.apiKey.first() })
}
