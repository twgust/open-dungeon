package dev.opendungeon.feature.newstory.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreationPathsTest {

    @Test
    fun `the custom path resumes when a draft is open`() = runTest {
        assertTrue(DefaultCreationPaths(InMemoryDraftRepository(emptyDraft)).customResumes())
        assertFalse(DefaultCreationPaths(InMemoryDraftRepository()).customResumes())
    }

    @Test
    fun `a draft that cannot be read counts as none`() = runTest {
        val drafts = InMemoryDraftRepository(emptyDraft).apply { failReads = 1 }

        assertFalse(DefaultCreationPaths(drafts).customResumes())
    }
}
