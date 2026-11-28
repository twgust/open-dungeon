package dev.opendungeon.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.opendungeon.core.designsystem.PaperIcons
import dev.opendungeon.core.designsystem.PaperTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class IntensityRadioRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val levels = listOf("Implied", "Fade to black", "Explicit")

    @Test
    fun `each level is a radio button named after it, in its place in the row`() {
        show(picked = 1)

        levels.forEachIndexed { index, name ->
            compose.onNodeWithContentDescription(name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assert(
                    SemanticsMatcher("column $index") {
                        it.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.columnIndex == index
                    },
                )
        }
    }

    @Test
    fun `only the picked level is selected`() {
        show(picked = 1)

        compose.onNodeWithContentDescription("Implied").assertIsNotSelected()
        compose.onNodeWithContentDescription("Fade to black").assertIsSelected()
        compose.onNodeWithContentDescription("Explicit").assertIsNotSelected()
    }

    @Test
    fun `tapping a level picks its index and moves the selection`() {
        var picked by mutableIntStateOf(1)
        val picks = mutableListOf<Int>()
        compose.setContent {
            PaperTheme {
                IntensityRadioRow(PaperIcons.Chili, levels, picked, onPick = {
                    picks += it
                    picked = it
                })
            }
        }

        compose.onNodeWithContentDescription("Explicit").performClick()

        assertEquals(listOf(2), picks)
        compose.onNodeWithContentDescription("Explicit").assertIsSelected()
        compose.onNodeWithContentDescription("Fade to black").assertIsNotSelected()
    }

    @Test
    fun `the picked name under the row is kept from screen readers, which hear it on the option`() {
        show(picked = 0)

        compose.onAllNodesWithText("Implied").assertCountEquals(0)
        compose.onAllNodesWithText("Implied", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun show(picked: Int) = compose.setContent {
        PaperTheme { IntensityRadioRow(PaperIcons.Chili, levels, picked, onPick = {}) }
    }
}
