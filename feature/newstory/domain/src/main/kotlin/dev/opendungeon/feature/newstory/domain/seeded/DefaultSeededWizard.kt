package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.CastSeed
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.FillBlank
import dev.opendungeon.domain.drafting.ResolveDraftingModel
import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.domain.drafting.SeedCast
import dev.opendungeon.domain.drafting.WriteOpening
import dev.opendungeon.domain.drafting.WriteSeeds
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.story.BeginStory
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.ModelLine
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * A thin coordinator, built like the custom wizard: verbs run one at a time on a single loop, which is the
 * only place the wizard's state changes. A model's work runs beside the loop and hands its result back
 * to it, tagged with the run it belongs to, so a result that arrives after the run was stopped is dropped.
 *
 * Seeds are dealt from the bundled deck, shuffled, and a model writes more only once the deck is used
 * up. A seed is dealt with its blanks empty: the player fills each from the words it came with, or their
 * own, and a model writes more words for a blank only when the player asks for them. The limits are the custom wizard's own rules ([LimitsPolicy]),
 * applied to an in-memory draft that also carries everything the model writes, so beginning the story
 * is the custom wizard's [BeginStory] too.
 *
 * Writing the story casts the two people, then writes the opening, then begins the story. If it fails,
 * the player is back on the limits, told why, with everything they chose kept; the people, once cast, are
 * kept as well while nothing they were cast from changes, so trying again only writes what is missing.
 */
@Suppress("TooManyFunctions")
internal class DefaultSeededWizard(
    private val scope: CoroutineScope,
    private val deck: SeedDeck,
    private val writeSeeds: WriteSeeds,
    private val fillBlank: FillBlank,
    private val castSeed: CastSeed,
    private val writeOpening: WriteOpening,
    private val beginStory: BeginStory,
    private val limits: LimitsPolicy,
    private val ids: IdGenerator,
    private val random: Random,
    drafter: ResolveDraftingModel,
) : SeededWizard {

    private val mutableState = MutableStateFlow<SeededState?>(null)
    override val state: StateFlow<SeededState?> = mutableState.asStateFlow()

    private val outcomeChannel = Channel<WizardOutcome>(Channel.BUFFERED)
    override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    // Read and written by the action loop only, so they need no locking.

    private var step = SeededStep.Seed

    /** The seed on screen, holding the player's words in the blanks they filled; `null` until started. */
    private var seed: Seed? = null

    /** The words that could fill each blank of [seed], in the order they were written. */
    private var suggestions: Map<String, List<String>> = emptyMap()

    /** The blanks of [seed] the player filled; the others show empty. */
    private var filled: Set<String> = emptySet()

    /** The blank whose suggestions are shown, if any. */
    private var picked: String? = null

    /** Seeds still to deal, in order. */
    private val coming = ArrayDeque<Seed>()

    /** Every seed dealt so far, so a model never writes one the player has seen. */
    private val dealt = mutableListOf<Seed>()

    /** The choices so far: the limits, and on the last step what the model wrote. Never stored until begun. */
    private var draft: StoryDraft? = null

    private var you = ""
    private var them = ""

    /** The people cast for [Casting.inputs], kept for another try with the same choices. */
    private var casting: Casting? = null

    /** What a model is writing now, if anything. */
    private var work: Work? = null

    private var drafting: DraftingModel? = null
    private var draftingKnown = false

    private var confirmingLeave = false

    /** The story began: the wizard is done, and every verb after it is ignored. */
    private var closed = false

    init {
        scope.launch {
            for (action in actions) runIsolated(action)
        }
        scope.launch {
            drafter()
                .catch { cause ->
                    // A failing source is a bug below; the wizard goes on without a model rather than die.
                    if (cause is CancellationException || cause !is Exception) throw cause
                    enqueue {
                        onDrafter(null)
                        report(WizardFailure.Unexpected(cause))
                    }
                }
                .collect { model -> enqueue { onDrafter(model) } }
        }
    }

    override fun start() = enqueue {
        if (draft != null) return@enqueue
        // The deck is read before the draft opens, so a deck that throws leaves start free to be tried again.
        val shuffled = deck.seeds.shuffled(random)
        draft = StoryDraft(DraftId(ids.next()), step = CreationStep.Limits)
        coming += shuffled
        coming.removeFirstOrNull()?.let(::deal) ?: writeSeeds()
    }

    override fun pick(blank: String) = enqueueOn(SeededStep.Seed) {
        if (seed?.blank(blank) == null) return@enqueueOn
        picked = blank
        render()
    }

    override fun fill(blank: String, words: String) = enqueueOn(SeededStep.Seed) {
        val current = seed ?: return@enqueueOn
        val fitting = words.filterNot { it in UNFILLABLE }.trim().take(Seed.MAX_FILL_LENGTH).trim()
        seed = current.filled(blank, fitting) ?: return@enqueueOn
        filled += blank
        picked = firstEmpty()
        render()
    }

    override fun suggestMore(blank: String) = enqueueOn(SeededStep.Seed) {
        val current = seed ?: return@enqueueOn
        if (work != null) return@enqueueOn
        val shown = current.blank(blank) ?: return@enqueueOn
        val model = drafting ?: return@enqueueOn report(WizardFailure.Drafting(DraftingError.NoModel))
        // The model is told every word offered so far, so it writes only new ones.
        val known = suggestions[blank].orEmpty().filterNot { it.equals(shown.fill, ignoreCase = true) }
        val asked = current.with(shown.copy(unused = known, used = emptyList()))
        launchWork(Work.Filling(blank)) { run ->
            val written = fillBlank(asked, blank, model)
            enqueue { onFilled(run, written) }
        }
    }

    override fun rollSeed() = enqueueOn(SeededStep.Seed) {
        if (work != null) return@enqueueOn
        val next = coming.removeFirstOrNull() ?: return@enqueueOn writeSeeds()
        deal(next)
    }

    override fun describe(person: Person, text: String) = enqueueOn(SeededStep.People) {
        val kept = text.take(MAX_DESCRIPTION_LENGTH)
        when (person) {
            Person.You -> you = kept
            Person.Them -> them = kept
        }
        render()
    }

    override fun select(choice: Choice) = enqueueOn(SeededStep.Limits) {
        val current = draft ?: return@enqueueOn
        limits.select(current, choice)?.let { draft = it.draft }
        render()
    }

    override fun submitOwn(text: String) = enqueueOn(SeededStep.Limits) {
        val current = draft ?: return@enqueueOn
        draft = limits.submitOwn(current, text).draft
        render()
    }

    override fun next() = enqueue {
        if (seed == null || closed) return@enqueue
        val following = when (step) {
            SeededStep.Seed -> if (allFilled()) SeededStep.People else return@enqueue
            SeededStep.People -> SeededStep.Limits
            SeededStep.Limits, SeededStep.Making -> return@enqueue
        }
        // Moving on keeps what is shown: words a model has not finished writing are not waited for.
        stopWork()
        step = following
        render()
    }

    override fun back() = enqueue {
        if (closed) return@enqueue
        when (step) {
            SeededStep.Seed -> {
                stopWork()
                outcomeChannel.send(WizardOutcome.Left)
            }
            SeededStep.People -> stepTo(SeededStep.Seed)
            SeededStep.Limits -> stepTo(SeededStep.People)
            SeededStep.Making -> {
                confirmingLeave = true
                render()
            }
        }
    }

    override fun stop() = enqueue {
        if (closed || work == null) return@enqueue
        stopWork()
        if (step == SeededStep.Making) step = SeededStep.Limits
        render()
    }

    override fun begin() = enqueueOn(SeededStep.Limits) {
        val chosen = draft ?: return@enqueueOn
        val current = seed ?: return@enqueueOn
        if (work != null) return@enqueueOn
        val model = drafting ?: return@enqueueOn report(WizardFailure.Drafting(DraftingError.NoModel))
        val inputs = Inputs(current, you.trim(), them.trim(), chosen.limits)
        val base = chosen.copy(
            genres = listOf(Phrase.Offered(current.genre)),
            tone = Phrase.Offered(current.tone),
            premise = Phrase.Offered(current.text),
            protagonist = null,
            cast = emptyList(),
            opening = "",
        )
        val cached = casting?.takeIf { it.inputs == inputs }?.cast
        draft = cached?.let { base.withCast(it, inputs) } ?: base
        step = SeededStep.Making
        launchWork(Work.Making()) { run -> make(run, base, inputs, cached, model) }
    }

    override fun answerLeave(answer: LeaveAnswer) = enqueue {
        if (!confirmingLeave) return@enqueue
        confirmingLeave = false
        if (answer == LeaveAnswer.Leave && step == SeededStep.Making) {
            stopWork()
            step = SeededStep.Limits
        }
        render()
    }

    /** Runs off the loop: casts if nothing cast fits, writes the opening, and hands each piece back. */
    private suspend fun make(run: Work, base: StoryDraft, inputs: Inputs, cached: SeedCast?, model: DraftingModel) {
        val cast = cached ?: when (
            val made =
                castSeed(
                    base,
                    inputs.seed.counterpartText,
                    inputs.you.ifEmpty {
                        null
                    },
                    inputs.them.ifEmpty { null },
                    model,
                )
        ) {
            is Outcome.Failure -> return enqueue { onMakingFailed(run, made.error) }
            is Outcome.Success -> made.value.also { enqueue { onCast(run, inputs, it) } }
        }
        writeOpening(base.withCast(cast, inputs), model).collect { progress ->
            when (progress) {
                is WritingProgress.Writing -> enqueue { onOpening(run, progress.text) }
                is WritingProgress.Written -> enqueue { onOpened(run, progress.text) }
                is WritingProgress.Failed -> enqueue { onMakingFailed(run, progress.error) }
            }
        }
    }

    private fun onCast(run: Work, inputs: Inputs, cast: SeedCast) {
        if (work !== run) return
        casting = Casting(inputs, cast)
        draft = draft?.withCast(cast, inputs)
        render()
    }

    private fun onOpening(run: Work, text: String) {
        if (work !== run) return
        draft = draft?.copy(opening = text.take(StoryDraft.MAX_OPENING_LENGTH))
        render()
    }

    private suspend fun onOpened(run: Work, text: String) {
        if (work !== run) return
        endWork()
        val finished = draft?.copy(opening = text.take(StoryDraft.MAX_OPENING_LENGTH)) ?: return
        draft = finished
        render()
        when (val begun = beginStory(finished)) {
            is Outcome.Failure -> {
                step = SeededStep.Limits
                render()
                report(WizardFailure.Storage(begun.error))
            }
            is Outcome.Success -> {
                closed = true
                outcomeChannel.send(WizardOutcome.Begun(begun.value))
            }
        }
    }

    private suspend fun onMakingFailed(run: Work, error: DraftingError) {
        if (work !== run) return
        endWork()
        step = SeededStep.Limits
        render()
        report(WizardFailure.Drafting(error))
    }

    /** New words join the end of the blank's suggestions; words it already offers are left out. */
    private suspend fun onFilled(run: Work, written: Outcome<Seed, DraftingError>) {
        if (work !== run) return
        endWork()
        val key = (run as Work.Filling).blank
        val known = suggestions[key].orEmpty()
        val fresh = when (written) {
            is Outcome.Failure -> return failed(WizardFailure.Drafting(written.error))
            is Outcome.Success -> written.value.blank(key)?.let { it.shown + it.unused }.orEmpty()
        }
        val seen = known.mapTo(HashSet()) { it.lowercase() }
        val more = fresh.filter { seen.add(it.lowercase()) }
        if (more.isEmpty()) return failed(WizardFailure.Drafting(DraftingError.Unusable))
        suggestions += key to known + more
        render()
    }

    private suspend fun failed(failure: WizardFailure) {
        render()
        report(failure)
    }

    /** Asks a model for more seeds than the deck had, and deals the first once they come. */
    private suspend fun writeSeeds() {
        val model = drafting ?: return report(WizardFailure.Drafting(DraftingError.NoModel))
        launchWork(Work.Replacing()) { run ->
            val written = writeSeeds(model, avoiding = dealt.toList())
            enqueue { onSeedsWritten(run, written) }
        }
    }

    private suspend fun onSeedsWritten(run: Work, written: Outcome<List<Seed>, DraftingError>) {
        if (work !== run) return
        endWork()
        when (written) {
            is Outcome.Failure -> {
                render()
                report(WizardFailure.Drafting(written.error))
            }
            is Outcome.Success -> {
                coming += written.value
                coming.removeFirstOrNull()?.let(::deal) ?: render()
            }
        }
    }

    /** Puts [next] on screen with its blanks empty and the first one picked. */
    private fun deal(next: Seed) {
        seed = next
        dealt += next
        suggestions = next.blanks.associate { it.key to it.shown + it.unused }
        filled = emptySet()
        picked = firstEmpty()
        render()
    }

    /** The first blank of the sentence still empty, if any. */
    private fun firstEmpty(): String? =
        seed?.parts?.filterIsInstance<Seed.Part.Filled>()?.map { it.key }?.firstOrNull { it !in filled }

    private fun allFilled(): Boolean = seed?.blanks?.all { it.key in filled } == true

    private fun stepTo(target: SeededStep) {
        step = target
        render()
    }

    private suspend fun onDrafter(model: DraftingModel?) {
        drafting = model
        draftingKnown = true
        render()
    }

    /** Starts [block] beside the loop as the one piece of work in progress. */
    private fun launchWork(run: Work, block: suspend (Work) -> Unit) {
        run.job = scope.launch(start = CoroutineStart.LAZY) { block(run) }
        work = run
        render()
        run.job.start()
    }

    private fun stopWork() {
        work?.job?.cancel()
        endWork()
    }

    /** Nothing is being written any more, so leaving needs no asking. */
    private fun endWork() {
        work = null
        confirmingLeave = false
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    /** A verb that means something only on [on], and only while the wizard is open. */
    private fun enqueueOn(on: SeededStep, action: suspend () -> Unit) = enqueue {
        if (!closed && step == on && seed != null) action()
    }

    private suspend fun report(failure: WizardFailure) {
        outcomeChannel.send(WizardOutcome.Failed(failure))
    }

    /**
     * The loop is the wizard's boundary, so one verb that throws must not end every verb after it. A throw
     * is a bug in a rule, never an expected failure (those come back typed): it is reported, and whatever
     * the verb started is stopped.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runIsolated(action: suspend () -> Unit) {
        val workBefore = work
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (work != null && work !== workBefore) stopWork()
            render()
            report(WizardFailure.Unexpected(e))
        }
    }

    private fun render() {
        val current = seed ?: return
        mutableState.value = stateOf(current, draft ?: return)
    }

    private fun stateOf(seed: Seed, draft: StoryDraft): SeededState {
        val running = work
        val idle = running == null
        val premise = seed.text
        val body = when (step) {
            SeededStep.Seed -> SeededBody.Seed(
                genre = seed.genre,
                parts = seed.parts.map { part ->
                    when (part) {
                        is Seed.Part.Text -> SeedPart.Text(part.text)
                        is Seed.Part.Filled -> SeedPart.Blank(
                            key = part.key,
                            kind = part.kind,
                            words = part.text.takeIf { part.key in filled },
                            picked = part.key == picked,
                        )
                    }
                },
                suggestions = picked?.let { key -> suggestionsOf(seed, key, running) },
                replacing = running is Work.Replacing,
            )
            SeededStep.People -> SeededBody.People(premise, you, them, seed.counterpartText, MAX_DESCRIPTION_LENGTH)
            SeededStep.Limits -> SeededBody.Limits(premise, draft.limits)
            SeededStep.Making -> SeededBody.Making(
                premise = premise,
                counterpart = seed.counterpartText,
                you = draft.protagonist?.shown(),
                them = draft.castInStory.firstOrNull()?.shown(),
                opening = draft.opening,
            )
        }
        val writes = step == SeededStep.Limits || step == SeededStep.Making || !idle
        return SeededState(
            step = step,
            progress = (step.ordinal + 1f) / SeededStep.entries.size,
            body = body,
            controls = SeededControls(
                canAdvance = when (step) {
                    SeededStep.Seed -> allFilled()
                    SeededStep.People -> true
                    SeededStep.Limits -> idle
                    SeededStep.Making -> false
                },
                begins = step == SeededStep.Limits,
                canRoll = step == SeededStep.Seed && idle,
                canStop = !idle,
                takesOwnWords = when (step) {
                    SeededStep.Seed -> picked != null
                    SeededStep.Limits -> limits.takesOwnWords(draft)
                    SeededStep.People, SeededStep.Making -> false
                },
            ),
            writer = if (writes && draftingKnown) ModelLine(model = drafting?.model?.name, writing = !idle) else null,
            confirmingLeave = confirmingLeave,
        )
    }

    private fun suggestionsOf(seed: Seed, key: String, running: Work?): Suggestions? {
        val blank = seed.blank(key) ?: return null
        return Suggestions(
            blank = key,
            kind = blank.kind,
            words = suggestions[key].orEmpty(),
            chosen = blank.fill.takeIf { key in filled },
            pending = running is Work.Filling && running.blank == key,
            canSuggestMore = running == null,
        )
    }

    private fun Character.shown(): SeededBody.Cast? = name?.let { SeededBody.Cast(it, role) }

    /** [cast] in the draft: the player's character, and the other person in the story. */
    private fun StoryDraft.withCast(cast: SeedCast, inputs: Inputs): StoryDraft = copy(
        protagonist = cast.you,
        cast = listOf(
            CastMember(
                character = cast.them,
                origin = if (inputs.them.isEmpty()) CastMember.Origin.Offered else CastMember.Origin.Own,
                inStory = true,
            ),
        ),
    )

    /** What the people are cast from: if none of it changed, the same people still fit. */
    private data class Inputs(val seed: Seed, val you: String, val them: String, val limits: ContentLimits)

    private class Casting(val inputs: Inputs, val cast: SeedCast)

    /** One run of a model's work. Each run is its own object, so a late result from an old run is told apart. */
    private sealed class Work {
        lateinit var job: Job

        /** More words for [blank]. */
        class Filling(val blank: String) : Work()

        /** New seeds, once the deck is used up. */
        class Replacing : Work()

        /** The people, the opening, then the story. */
        class Making : Work()
    }

    companion object {
        /** A line or two about someone: enough to steer the model, short enough to stay a sketch. */
        const val MAX_DESCRIPTION_LENGTH: Int = 200

        /** Characters that cannot stand in a blank, left out of the player's own words. */
        private const val UNFILLABLE = "{}\n\r"
    }
}

@ContributesBinding(AppScope::class)
public class DefaultSeededWizardFactory internal constructor(
    private val deck: SeedDeck,
    private val writeSeeds: WriteSeeds,
    private val fillBlank: FillBlank,
    private val castSeed: CastSeed,
    private val writeOpening: WriteOpening,
    private val beginStory: BeginStory,
    private val limits: LimitsPolicy,
    private val ids: IdGenerator,
    private val drafter: ResolveDraftingModel,
    private val random: Random,
) : SeededWizard.Factory {

    @Inject
    public constructor(
        deck: SeedDeck,
        writeSeeds: WriteSeeds,
        fillBlank: FillBlank,
        castSeed: CastSeed,
        writeOpening: WriteOpening,
        beginStory: BeginStory,
        limits: LimitsPolicy,
        ids: IdGenerator,
        drafter: ResolveDraftingModel,
    ) : this(deck, writeSeeds, fillBlank, castSeed, writeOpening, beginStory, limits, ids, drafter, Random.Default)

    override fun create(scope: CoroutineScope): SeededWizard = DefaultSeededWizard(
        scope = scope,
        deck = deck,
        writeSeeds = writeSeeds,
        fillBlank = fillBlank,
        castSeed = castSeed,
        writeOpening = writeOpening,
        beginStory = beginStory,
        limits = limits,
        ids = ids,
        random = random,
        drafter = drafter,
    )
}
