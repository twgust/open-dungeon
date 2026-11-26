# Open Dungeon

An Android text adventure in the vein of AI Dungeon. Players bring their own OpenRouter key; stories will
live in Firebase. See the project roadmap for what comes next.

## Build

Requires JDK 21 and an Android SDK with platform `android-37.2`.

```sh
./gradlew test lint assembleDebug
```

## Stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.4, warnings are errors, explicit API mode in JVM modules |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVI (`core:mvi`): immutable state, intents in, one-off effects out |
| DI | [Metro](https://github.com/ZacSweers/metro) with contribution-based graphs (`@ContributesBinding`, `@ContributesIntoMap`) and `metrox` for ViewModels and constructor-injected Activities |
| Networking | Ktor client (OkHttp engine) + kotlinx.serialization |
| Generation | [Koog](https://github.com/JetBrains/koog) `OpenRouterLLMClient` behind `TextGenerator` / `StructuredGenerator` |
| Storage | Room for drafts (schemas exported to `core/database/schemas`); DataStore for the API key, AES-GCM encrypted with an Android Keystore key |
| Build | Gradle 9, version catalog, convention plugins in `build-logic` |
| Tests | JUnit 5, kotlinx-coroutines-test, Turbine, Ktor `MockEngine`, OkHttp `MockWebServer`; Robolectric only for Room |

## Modules

```
app                      Application, Metro AppGraph, MainActivity; binds every data implementation
feature/connect          Connect an OpenRouter key and list the models it unlocks
feature/newstory/ui      Story creation screens and their thin ViewModel
feature/newstory/domain  StoryWizard interactor, one StepPolicy per step, vocabulary    (JVM)
data/openrouter          Model catalog and account repositories over the REST client   (JVM)
data/story               StoryDraftRepository over Room (Android)
data/generation          Text and structured generation on Koog's OpenRouter client    (JVM)
domain/openrouter        ModelCatalogRepository, OpenRouterAccountRepository           (JVM)
domain/story             Drafts, characters, takes, the sentence builder, StoryDraftRepository (JVM)
domain/generation        TextGenerator, StructuredGenerator, sampling, typed errors    (JVM)
core/designsystem        Paper theme, bundled fonts, pills, dice, cards (Android)
core/ui                  Compose helpers
core/database            Room database, entities, DAOs (Android)
core/datastore           Encrypted on-device key storage (Android)
core/credentials         ApiKeyStore, the contract core/datastore implements           (JVM)
core/network             OpenRouter REST client and DTOs                                   (JVM)
core/mvi                 MviViewModel base                                                 (JVM)
core/model               Domain types: ApiKey, AiModel, ModelParameter, Outcome, DataError (JVM)
```

Dependencies point downwards only. A repository's interface lives in a `domain` module and its
implementation in the matching `data` module. Features depend on `core` and `domain` modules and on their
own feature's modules, never on `data` modules or on each other; the feature convention plugin fails the
build otherwise. Screens render domain state directly: `compose-stability.conf` declares the domain
models stable, so there are no mirror UI types. Everything that can be pure Kotlin is, so most tests run
on the JVM without Robolectric or an emulator.

## OpenRouter

The network layer follows the current official API, not earlier projects:

- Base URL `https://openrouter.ai/api/v1/`, bearer auth with keys of the form `sk-or-v1-<64 hex>`.
- Attribution headers are `HTTP-Referer` and `X-OpenRouter-Title`.
- Models come from `GET /models/user`, which filters the catalog by the key's provider preferences,
  privacy settings and guardrails. It is paginated (`offset`, `limit` up to 1000, `links.next`).
- Each model's `supported_parameters` is mapped to `ModelParameter`; names the app does not know yet are
  kept as `ModelParameter.Unknown` instead of failing, as the [versioning policy](https://openrouter.ai/docs/api_reference/versioning)
  asks of clients.
- Prices are decimal strings; `"-1"` marks dynamic routers and `"0"` free variants. Both are modelled
  explicitly in `ModelPricing`.
- Key validation uses `GET /key`. Errors use the envelope `{"error": {"code", "message", "metadata"}}`.

Source of truth: <https://openrouter.ai/openapi.json> and <https://openrouter.ai/docs/llms.txt>.
