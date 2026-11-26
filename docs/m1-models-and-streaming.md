# M1: models, parameters and streaming

What finishes M1 after the key and network layers: the catalog on the device, a picker, a parameter
editor generated from each model's own data, and a screen that streams a reply. Exit criterion, from the
roadmap: paste a key, pick any model, see its exact parameter list, change two parameters, stream a
reply, cancel mid-stream.

Wire facts below were checked against <https://openrouter.ai/openapi.json> and the
[parameters](https://openrouter.ai/docs/api_reference/parameters.md) and
[streaming](https://openrouter.ai/docs/api_reference/streaming.md) pages on 2026-11-26.

## The catalog

- **Source.** `GET /models/user`, paginated, so the list already honours the key's provider
  preferences, privacy settings and guardrails.
- **Selectable means chat-capable.** Each model lists the request paths that serve it in `endpoints`.
  About a fifth of today's catalog is served only by `/api/v1/batches` (the `:batch` variants) and cannot
  stream a reply. Those are dropped. A model without the field is kept, since the field is optional.
- **Cached in Room.** The whole list is replaced in one transaction. The screens read only the cache, so
  they render without the network. The picker refreshes when the cache is older than a day, and on
  demand. Disconnecting clears it: `/models/user` is specific to the key.
- **Sorting is OpenRouter's.** `sort` on `/models/user` ranks server side (popularity, weekly tokens,
  newest, price, context, throughput, latency, intelligence). A ranking is fetched the first time a sort
  is chosen, kept in Room as an ordered list of ids, and refetched with the catalog. Sorts for other
  modalities (`decision-high-to-low`), retired indexes (`coding`, `agentic`) and professional domains
  (legal, finance and so on) are not offered.
- **Filters are local.** Search over name, id and description; input modality; free only; minimum
  context; tools.

## Per-provider detail

`GET /models/{author}/{slug}/endpoints`, fetched when the model's page opens and never cached. A model's
`supported_parameters` is the union over its providers, so the page shows each provider with its own
parameters, context, price, quantization and uptime.

## Choice and tuning

One chosen model, and a tuning per model id, both in Room. Switching models and back keeps each model's
tuning. A tuning holds only what the player changed; everything else is left to the provider, as
OpenRouter recommends (omitting a parameter is not the same as sending its default).

## The parameter editor

`ParameterSheet` (domain) turns a model into rows, one per entry in `supported_parameters`, in the
model's own order:

| Parameter | Control | Range, from the parameters page |
| --- | --- | --- |
| `temperature` | slider | 0 to 2 |
| `top_p`, `min_p`, `top_a` | slider | 0 to 1 |
| `frequency_penalty`, `presence_penalty` | slider | -2 to 2 |
| `repetition_penalty` | slider | 0 to 2 |
| `top_k` | whole number | 0 or above, 0 is off |
| `max_tokens` / `max_completion_tokens` | whole number | 1 to the top provider's maximum |
| `seed` | whole number | any |
| `stop` | up to four sequences | |
| `verbosity` | choice | low, medium, high, xhigh, max |
| `reasoning`, `reasoning_effort` | choice | the model's own `supported_efforts`, plus off unless mandatory, plus a token budget where `supports_max_tokens` and `reasoning` is listed |

`max_tokens` and `max_completion_tokens` are one setting; it is sent under `max_completion_tokens` when
the model lists it, since `max_tokens` is deprecated. `reasoning` and `reasoning_effort` are one
setting, sent as `reasoning.effort` (or `reasoning.enabled: false` for off) when the model lists
`reasoning`, else as `reasoning_effort`. `include_reasoning` is a deprecated alias and gets no row of its
own.

Every other parameter is something the app's code sets per request, not a preference: `tools`,
`tool_choice`, `parallel_tool_calls`, `response_format`, `structured_outputs`, `strict_tool_schemas`,
`logit_bias`, `logprobs`, `top_logprobs`, `prediction`, `web_search_options`. They are listed as
capabilities so the list stays exact. Names the app does not know are listed by their wire name.

A slider starts at the model's `default_parameters` value when it states one, else at the documented
conventional default, and shows "default" until moved; it moves in hundredths. A whole number outside its
row's bounds is not taken. Reset returns a row to the provider's choice. Edits show at once and are stored
through a conflated queue; a failed write puts the editor back to what is stored and says so.

## Sending

A value is sent only when the model lists its parameter. When a request carries any tuned value,
`provider.require_parameters` is set, so OpenRouter routes only to providers that honour all of them.
If no provider can, OpenRouter answers 404, which surfaces as "No provider takes these settings".

## Streaming and cost

Generation stays on Koog. Koog 1.3's `OpenRouterLLMClient` drops two things the chat completions stream
carries: `usage.cost` on the final accounting chunk, and the mid-stream `error` object. The roadmap's
mitigation applies: `OpenRouterChatClient` is our own Koog `LLMClient`, built on Koog's
`AbstractOpenAILLMClient` (message conversion, SSE, tools and structured output stay Koog's), with
request and response types written from the spec. It sends every tunable parameter as a typed field,
reads cost and reasoning tokens, and reports a mid-stream error as a failure with its code.

`TextEvent.Completed` carries `Usage` with tokens and the cost OpenRouter charged.

## Screens

- **Home** (the Connect screen once connected) shows the key and the chosen model. Tapping the model
  opens the picker; Try opens the try screen.
- **Picker**: search, filters and OpenRouter's sorts over the cached catalog. A sort shows at once and is
  taken back if its ranking cannot be fetched. Choosing a model stores it and opens its editor.
- **Editor**: the parameter rows, the capabilities, and the providers with their own parameters.
- **Try**: a prompt, the streamed reply with its reasoning folded away, and the usage line with tokens and
  cost. The interactor owns the run: sending, stopping, and leaving. Leaving while a reply streams asks
  first; stopping keeps the text so far. Cancelling the collector closes the connection, which stops the
  provider and billing where the provider supports it.

## Navigation

Navigation 3 with a saved back stack. Each entry gets its own `ViewModelStore`, so a screen's work,
a stream included, ends when the screen leaves the stack.
