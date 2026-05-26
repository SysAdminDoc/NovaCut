package com.novacut.editor.engine

import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema-version gate for [AutoSaveState] / [ProjectAutoSave.LoadOutcome].
 *
 * The contract we lock here:
 *  - `serialize()` writes both `version` and `schemaVersion` set to FORMAT_VERSION.
 *  - `peekSchemaVersion()` prefers `schemaVersion`, falls back to `version`,
 *    returns null on missing key or malformed JSON.
 *  - Loaders that consume [ProjectAutoSave.LoadOutcome] must surface
 *    `FutureSchema` for any payload whose schema version exceeds the build's
 *    `FORMAT_VERSION` — never silently fall through to a corrupt-recovery branch
 *    that could downgrade the project to a stale backup.
 *
 * This test exercises the pure helpers only; the `loadRecoveryDataWithOutcome`
 * suspend orchestration lives behind Android filesystem APIs and is covered by
 * its own instrumentation test once the editor UI adopts the new outcome path.
 */
class AutoSaveSchemaVersionTest {

    @Test
    fun serialize_writesBothVersionFields() {
        val state = AutoSaveState(
            projectId = "p",
            timestamp = 1L,
            playheadMs = 0L,
            tracks = listOf(
                Track(id = "t", type = TrackType.VIDEO, index = 0, clips = emptyList())
            ),
            textOverlays = emptyList(),
        )

        val raw = state.serialize()
        val obj = JSONObject(raw)

        assertEquals(
            "version field must round-trip the build's FORMAT_VERSION",
            AutoSaveState.FORMAT_VERSION,
            obj.optInt("version", -1)
        )
        assertEquals(
            "schemaVersion field must round-trip the build's FORMAT_VERSION",
            AutoSaveState.FORMAT_VERSION,
            obj.optInt("schemaVersion", -1)
        )
    }

    @Test
    fun peekSchemaVersion_prefersSchemaVersionOverVersion() {
        val raw = """{ "version": 1, "schemaVersion": 7, "projectId": "p" }"""
        assertEquals(7, AutoSaveState.peekSchemaVersion(raw))
    }

    @Test
    fun peekSchemaVersion_fallsBackToVersionWhenSchemaVersionMissing() {
        val raw = """{ "version": 3, "projectId": "p" }"""
        assertEquals(3, AutoSaveState.peekSchemaVersion(raw))
    }

    @Test
    fun peekSchemaVersion_returnsNullWhenNeitherFieldPresent() {
        val raw = """{ "projectId": "p" }"""
        assertNull(AutoSaveState.peekSchemaVersion(raw))
    }

    @Test
    fun peekSchemaVersion_returnsNullOnMalformedJson() {
        assertNull(AutoSaveState.peekSchemaVersion("{ this is not json"))
        assertNull(AutoSaveState.peekSchemaVersion(""))
        assertNull(AutoSaveState.peekSchemaVersion("[1,2,3]"))
    }

    @Test
    fun peekSchemaVersion_treatsNegativeAsValid() {
        // optInt accepts negative integers; peekSchemaVersion does no clamp.
        // Negative is still a signal worth preserving so callers can render it
        // as "Unknown schema" rather than crash on an empty field.
        val raw = """{ "schemaVersion": -1, "projectId": "p" }"""
        assertEquals(-1, AutoSaveState.peekSchemaVersion(raw))
    }

    @Test
    fun loadOutcome_sealedHierarchyEnumeratesEveryReason() {
        val cases: List<ProjectAutoSave.LoadOutcome> = listOf(
            ProjectAutoSave.LoadOutcome.Loaded(
                AutoSaveState(
                    projectId = "p",
                    timestamp = 0L,
                    playheadMs = 0L,
                    tracks = emptyList(),
                    textOverlays = emptyList(),
                )
            ),
            ProjectAutoSave.LoadOutcome.FutureSchema(
                fileVersion = AutoSaveState.FORMAT_VERSION + 1,
                supportedVersion = AutoSaveState.FORMAT_VERSION,
            ),
            ProjectAutoSave.LoadOutcome.Corrupt(IllegalStateException("test")),
            ProjectAutoSave.LoadOutcome.NotFound,
        )
        // The exhaustiveness here is the test — adding a new outcome forces
        // every consumer (recovery dialog, settings UI) to handle it.
        for (outcome in cases) {
            val rendered: String = when (outcome) {
                is ProjectAutoSave.LoadOutcome.Loaded -> "loaded"
                is ProjectAutoSave.LoadOutcome.FutureSchema -> "future:${outcome.fileVersion}"
                is ProjectAutoSave.LoadOutcome.Corrupt -> "corrupt:${outcome.cause.message}"
                ProjectAutoSave.LoadOutcome.NotFound -> "not-found"
            }
            assertTrue(rendered.isNotEmpty())
        }
    }
}
