package com.novacut.editor.engine

import com.novacut.editor.engine.ProjectShortcutPlanner.DynamicShortcut
import com.novacut.editor.engine.ProjectShortcutPlanner.ShortcutId
import com.novacut.editor.engine.ProjectShortcutPlanner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the dynamic-shortcut decision table that the eventual
 * `ProjectListViewModel.refreshDynamicShortcuts()` call (Android-only side
 * effect via `ShortcutManagerCompat.setDynamicShortcuts`) will consume.
 *
 * The five interesting cases:
 *  1. Fresh install (no last project) → no dynamic shortcuts.
 *  2. User opted out in Settings → no dynamic shortcuts.
 *  3. Last project, no recovery → only the "Open <name>" entry.
 *  4. Last project + recovery → "Resume" ranked first, "Open" second.
 *  5. Excessive ranking → capped at MAX_DYNAMIC_SHORTCUTS.
 */
class ProjectShortcutPlannerTest {

    @Test
    fun freshInstall_returnsEmpty() {
        val state = State(
            lastProjectId = null,
            lastProjectName = null,
            hasRecoveryForLast = false,
        )
        assertTrue(ProjectShortcutPlanner.planDynamic(state).isEmpty())
    }

    @Test
    fun userOptedOut_returnsEmptyEvenWithFullState() {
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "My Project",
            hasRecoveryForLast = true,
            userOptedOut = true,
        )
        assertTrue(ProjectShortcutPlanner.planDynamic(state).isEmpty())
    }

    @Test
    fun blankProjectId_treatedAsNoLastProject() {
        // Defensive — Room can in theory return an empty string for a corrupt row.
        val state = State(
            lastProjectId = "",
            lastProjectName = "X",
            hasRecoveryForLast = true,
        )
        assertTrue(ProjectShortcutPlanner.planDynamic(state).isEmpty())
    }

    @Test
    fun lastProjectWithoutRecovery_returnsOnlyOpenShortcut() {
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "My Project",
            hasRecoveryForLast = false,
        )
        val shortcuts = ProjectShortcutPlanner.planDynamic(state)
        assertEquals(1, shortcuts.size)
        assertEquals(ShortcutId.OPEN_LAST_PROJECT, shortcuts.single().shortcutId)
        // Project name flows into the short label.
        assertEquals("My Project", shortcuts.single().shortLabel)
    }

    @Test
    fun lastProjectWithRecovery_resumeRanksAheadOfOpen() {
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "My Project",
            hasRecoveryForLast = true,
        )
        val shortcuts = ProjectShortcutPlanner.planDynamic(state)
        assertEquals(2, shortcuts.size)
        assertEquals(ShortcutId.RESUME_RECOVERED, shortcuts[0].shortcutId)
        assertEquals(ShortcutId.OPEN_LAST_PROJECT, shortcuts[1].shortcutId)
        assertEquals(0, shortcuts[0].rank)
        assertEquals(1, shortcuts[1].rank)
    }

    @Test
    fun shortcutPayload_includesProjectIdExtra() {
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "Edit",
            hasRecoveryForLast = true,
        )
        val shortcuts = ProjectShortcutPlanner.planDynamic(state)
        for (s in shortcuts) {
            assertEquals(
                "Project ID payload missing from ${s.shortcutId}",
                "p1",
                s.extras[ProjectShortcutPlanner.EXTRA_PROJECT_ID]
            )
        }
    }

    @Test
    fun emptyProjectName_fallsBackToLastProject() {
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "   ",
            hasRecoveryForLast = false,
        )
        val open = ProjectShortcutPlanner.planDynamic(state).single()
        assertEquals(ShortcutId.OPEN_LAST_PROJECT, open.shortcutId)
        assertEquals("Last Project", open.shortLabel)
    }

    @Test
    fun longProjectName_isTruncatedForShortLabel() {
        val name = "A very very very very long project title that no launcher chip will render"
        val state = State(
            lastProjectId = "p1",
            lastProjectName = name,
            hasRecoveryForLast = false,
        )
        val open = ProjectShortcutPlanner.planDynamic(state).single()
        assertTrue(
            "Short label must stay under 25 chars (was ${open.shortLabel.length})",
            open.shortLabel.length <= 25
        )
    }

    @Test
    fun resultIsCappedAtMaxDynamicShortcuts() {
        // Even with both entries active, total stays at the cap (which is 2
        // today). A future state with three signals would be silently trimmed
        // by the planner — exercising the contract here so a future bump of
        // MAX_DYNAMIC_SHORTCUTS doesn't accidentally over-push.
        val state = State(
            lastProjectId = "p1",
            lastProjectName = "X",
            hasRecoveryForLast = true,
        )
        val shortcuts = ProjectShortcutPlanner.planDynamic(state)
        assertTrue(
            "Result exceeds MAX_DYNAMIC_SHORTCUTS (${shortcuts.size} > ${ProjectShortcutPlanner.MAX_DYNAMIC_SHORTCUTS})",
            shortcuts.size <= ProjectShortcutPlanner.MAX_DYNAMIC_SHORTCUTS
        )
    }
}
