package dev.opendungeon.domain.generation

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelPricing
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RequestsTest {

    private val model = AiModel(
        id = ModelId("a/b"),
        name = "B",
        description = null,
        contextLength = null,
        maxCompletionTokens = null,
        pricing = ModelPricing.Free,
        inputModalities = setOf("text"),
        outputModalities = setOf("text"),
        supportedParameters = emptySet(),
        defaultParameters = DefaultParameters(),
        reasoning = null,
        isModerated = null,
    )

    @Test
    fun `a request without messages is a bug`() {
        assertFailsWith<IllegalArgumentException> { TextRequest(model = model, messages = emptyList()) }
    }
}
