# Continuity harness

A command-line tool that measures how well a context policy keeps a long story straight. It is the
first step of the context engine: every later choice (how many verbatim turns, which summaries,
what retrieval) gets compared on its numbers rather than on feel. It never ships in the app.

It measures three failures:

- **Recall.** Does the model still know a fact from many turns back? Grouped by how far back the
  fact is.
- **Leaks.** Does a character know something they never saw or heard? The research behind the
  knowledge ledger says models are bad at deciding this themselves (FANToM, TimeChara), so the
  harness checks it directly.
- **Contradictions.** Does the story contradict itself, such as a dead man walking in or the key in
  the wrong hands?

It also measures the opposite of leaking. **Character recall** shows whether a character still
knows what they *should* know. A policy that hides too much makes characters amnesiac.

## Running it

The harness uses your own OpenRouter key, read from the environment. Nothing is stored and nothing
is committed.

```sh
export OPENROUTER_API_KEY=sk-or-v1-…

# Structure only, no key needed.
./gradlew -q :tools:continuity:run --args="check saltmere"

# Have the judge confirm that every fact really is stated in its turn.
./gradlew -q :tools:continuity:run --args="check saltmere --verify"

# Compare the default policies: full, chars:12000, recent:3, recent:6, perceived/full.
./gradlew -q :tools:continuity:run --args="run saltmere"

# Pick the policies, the model and the judge.
./gradlew -q :tools:continuity:run --args="run saltmere --policy recent:2 --policy recent:3 \
  --policy recent:5 --model z-ai/glm-5.3-flash --judge-model deepseek/deepseek-v4.1-flash --samples 5"

# Stretch the scenario to 300 turns, then run the long version.
./gradlew -q :tools:continuity:run --args="pad saltmere --turns 300"
./gradlew -q :tools:continuity:run --args="run build/continuity/saltmere-300.json"
```

On Windows, run the same commands from the repository folder in PowerShell. Set the key with
`$env:` and call the wrapper as `.\gradlew.bat`. The key lasts only for that window.

```powershell
cd C:\path\to\open-dungeon
$env:OPENROUTER_API_KEY = "sk-or-v1-…"
.\gradlew.bat -q :tools:continuity:run --args="check saltmere"
.\gradlew.bat -q :tools:continuity:run --args="run saltmere"
```

Everything inside `--args="…"` goes to the harness, so `run saltmere` on its own is not a command.
The harness needs JDK 21, like the rest of the build.

`--key-file <path>` reads the key from a file instead. Paths are relative to the repository root.
Reports go to `build/continuity/<scenario>-<time>/`: `report.md` to read, `report.json` with every
number, and `samples.json` with every answer and verdict.

### Models and cost

The default model is `deepseek/deepseek-v4.1-flash`, which is cheap but competent. Prices checked on
OpenRouter's live catalog on 2026-11-27: $0.051 in and $0.567 out per million tokens, with structured
outputs and reasoning that can be turned off. The GLM alternative is `z-ai/glm-5.3-flash`, at $0.15
and $0.50, with reasoning that cannot be turned off. Every id is looked up in the live catalog
before anything is spent, and a retired id fails with nearby suggestions.

`--reasoning least` (the default) turns reasoning off where the model allows it, and otherwise uses
the lowest effort. The judge always runs at temperature 0 with the least reasoning.

A default run on the bundled scenario makes 22 probes × 5 policies × 3 samples = 330 answers plus
330 judge calls. On DeepSeek V4.1 Flash that is a few cents.

## How it works

```
scenario ──► policy session ──► probe prompt ──► model under test ──► judge ──► report
            (turn by turn)     (setup + context    (TextGenerator)    (Structured-
                                + the ask)                             Generator)
```

- **Scenario** (`scenario/`). A scripted story in JSON. It holds every turn, who besides the
  protagonist perceived each passage, the facts each turn establishes (with `supersededBy` when a
  fact stops holding), and the probes. `ScenarioCheck` rejects a broken scenario before any model
  sees it.
- **Policy** (`context/`). A `ContextPolicy` gets the story one turn at a time and says what a
  reader is given at any point. This interface is what the context engine will implement. The
  baselines are a transcript window (`full`, `recent:N`, or `chars:N`, which is how the app chooses
  today) and an optional `perceived/` projection. Under that projection a character reader gets only
  the passages they perceived: it is the leak-free reference the knowledge ledger should reach.
- **Probe** (`probe/`). A probe is asked right after a turn, in one of three forms:
  - `question`: a reader's question about the story. It tests whether the context still holds the
    fact.
  - `action`: the player's next turn, answered with the app's narrator instructions. It tests
    whether the story continues without contradiction.
  - `voice`: a line spoken to a character, who answers. It tests what that character knows, and
    what they should not.
- **Judge** (`judge/`). A model with structured output reads the answer and nothing else, and says
  for each fact whether the answer *asserted*, *contradicted* or left it *absent*. A probe lists the
  facts to `expect` (missing one is a recall failure), to `forbid` (stating one is a leak) and to
  `guard` (contradicting one is a contradiction).
- **Report** (`report/`). Rates per policy, each with a 95% Wilson interval, because a few dozen
  probes is a small sample and two policies only differ when their intervals say so.
- **Pad** (`pad/`). Writes quiet interludes between the hand-written turns so a story of 29 turns
  becomes one of 300 or more. Facts and probes stay on the hand-written turns, so every fact stays
  exact while the distances grow. The judge rejects any interlude that states a fact or contradicts
  one that still holds, and that interlude is written again. After three failed attempts the
  padding stops rather than ship a bad story.

The harness runs on the app's own stack: Koog through `:data:generation`, the OpenRouter catalog
through `:data:openrouter`, and Metro for wiring. A run therefore exercises the same request path
players do.

## The bundled scenario

`saltmere`: a courier, a stolen ledger, a poisoned harbourmaster and a lighthouse kept dark. It has
29 turns, 25 facts and 22 probes, built to hit the failure modes in the research:

- A secret only the protagonist sees (the poisoning), later told to one character (Kael) but never
  to another (Oren). The leak probes come before and after the telling.
- Something said after a character leaves the room (Oren's suspicion). Kael is asked about it 3
  turns and 23 turns later.
- An object that changes hands twice (the brass key). The question is who holds it now.
- A death (Fenn). An in-story action tries to visit him.
- Details mentioned once and asked about much later: the cut hand, the burned letter, the
  captain's name.
- Common knowledge the story only states in a scene one character missed: the whole town knows the
  lighthouse is dark, but Mara was not there when the story said so. A strict perceived-only view
  fails this probe on purpose. That is the over-separation the ledger's public facts must fix.

## Writing a scenario

Copy `src/main/resources/scenarios/saltmere.json`. The rules `check` enforces:

- The first turn is the opening and has no player line. Every later turn has one.
- `present` lists the cast in the scene besides the protagonist. A passage's `perceivedBy`, when
  given, must be a subset of it, and `[]` means only the protagonist.
- Each fact is established in a hand-written turn. A fact named in `supersededBy` must be
  established later than the fact it replaces.
- A probe may only `expect` or `guard` facts established by its turn, and may not `expect` one that
  has been replaced.

Write each fact as one plain claim. The judge sees only the claim and the answer, so a fact that
needs context to understand will be judged badly. Then run `check --verify`.

## Adding a policy

Implement `ContextPolicy` and register it in `PolicySpec`. A session is given each turn through
`advance` and asked for a reader's context through `contextFor`. An engine that extracts beats or
builds summaries as turns arrive does that work in `advance`.

## Limits

- The judge is a model and can be wrong. `samples.json` holds every quote it based a verdict on, so
  a surprising number can be traced to the exact words.
- Judge calls are not counted in the reported cost, only the calls to the model under test.
- Interludes in different gaps are written in parallel and cannot see each other. Each one reads
  the three turns before it within its own gap.
